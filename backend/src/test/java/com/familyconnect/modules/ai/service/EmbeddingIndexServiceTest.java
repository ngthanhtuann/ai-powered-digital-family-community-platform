package com.familyconnect.modules.ai.service;

import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.ai.api.AiGateway.SourceType;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra lập chỉ mục với PostgreSQL thật (pgvector) nhưng không gọi Gemini:
 * EmbeddingPipelineService được mock, không gửi dữ liệu ra ngoài (privacy.md).
 */
@SpringBootTest
@Transactional
class EmbeddingIndexServiceTest {

    @Autowired
    private AiGateway aiGateway;

    @Autowired
    private EmbeddingChunkRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private EmbeddingPipelineService pipeline;

    private final UUID familyId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        float[] vector = new float[EmbeddingPipelineService.DIMENSIONS];
        Arrays.fill(vector, 0.01f);
        when(pipeline.isConfigured()).thenReturn(true);
        when(pipeline.model()).thenReturn("gemini-embedding-001");
        when(pipeline.createEmbedding(anyString())).thenReturn(vector);
    }

    @Test
    void aiGatewayIsTheEmbeddingIndexService() {
        assertThat(aiGateway).isInstanceOf(EmbeddingIndexService.class);
    }

    @Test
    void upsertStoresChunkWithMetadataForEverySourceType() {
        for (SourceType type : SourceType.values()) {
            UUID id = UUID.randomUUID();
            aiGateway.upsertSource(type, id, familyId, null, "Ngày giỗ tổ năm 2026. Cả họ tụ tập đông đủ.");
            assertThat(repository.countActiveBySource(type.name(), id)).isEqualTo(1);
        }
        String metadata = jdbc.queryForObject(
                "select metadata::text from ai_embedding_chunk where family_id = ? limit 1", String.class, familyId);
        assertThat(metadata).contains("gemini-embedding-001").contains("chunkCount");
    }

    @Test
    void unchangedContentIsNotEmbeddedAgain() {
        aiGateway.upsertSource(SourceType.STORY, sourceId, familyId, null, "Câu chuyện không đổi.");
        aiGateway.upsertSource(SourceType.STORY, sourceId, familyId, null, "Câu chuyện không đổi.");

        verify(pipeline, times(1)).createEmbedding(anyString());
        assertThat(repository.countActiveBySource("STORY", sourceId)).isEqualTo(1);
    }

    @Test
    void changedContentReplacesOldChunks() {
        aiGateway.upsertSource(SourceType.DOCUMENT, sourceId, familyId, null, "Bản cũ");
        aiGateway.upsertSource(SourceType.DOCUMENT, sourceId, familyId, null, "Bản mới");

        assertThat(repository.countActiveBySource("DOCUMENT", sourceId)).isEqualTo(1);
        assertThat(repository.findActiveHashes("DOCUMENT", sourceId)).hasSize(1);
        Integer deleted = jdbc.queryForObject(
                "select count(*) from ai_embedding_chunk where source_id = ? and deleted_at is not null",
                Integer.class, sourceId);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void longContentIsSplitIntoChunksOf500Words() {
        String content = "từ ".repeat(1200);
        aiGateway.upsertSource(SourceType.POST, sourceId, familyId, null, content);

        assertThat(repository.countActiveBySource("POST", sourceId)).isEqualTo(3);
    }

    @Test
    void removeSourceSoftDeletesAllChunks() {
        aiGateway.upsertSource(SourceType.PERSON, sourceId, familyId, null, "Họ tên: Nguyễn Văn An. Quan hệ: Con.");
        aiGateway.removeSource(SourceType.PERSON, sourceId);

        assertThat(repository.countActiveBySource("PERSON", sourceId)).isZero();
    }

    @Test
    void withoutApiKeyNothingIsIndexedAndNoErrorIsThrown() {
        when(pipeline.isConfigured()).thenReturn(false);

        aiGateway.upsertSource(SourceType.STORY, sourceId, familyId, null, "Nội dung");

        verify(pipeline, never()).createEmbedding(anyString());
        assertThat(repository.countActiveBySource("STORY", sourceId)).isZero();
    }

    @Test
    void geminiFailureDoesNotPropagateToCaller() {
        when(pipeline.createEmbedding(anyString())).thenThrow(new IllegalStateException("Gemini lỗi"));

        aiGateway.upsertSource(SourceType.STORY, sourceId, familyId, null, "Nội dung");

        assertThat(repository.countActiveBySource("STORY", sourceId)).isZero();
    }
}
