package com.familyconnect.modules.ai.service;

import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.ai.api.AiGateway.SourceType;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Module nguồn (vd. HeritageService) gọi AiGateway trong afterCommit; chunk phải được lưu thật. */
@SpringBootTest
class AiGatewayAfterCommitTest {

    @Autowired
    private AiGateway aiGateway;

    @Autowired
    private EmbeddingChunkRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private EmbeddingPipelineService pipeline;

    @Test
    void upsertAndRemoveCalledAfterCommitArePersisted() {
        float[] vector = new float[EmbeddingPipelineService.DIMENSIONS];
        Arrays.fill(vector, 0.02f);
        when(pipeline.isConfigured()).thenReturn(true);
        when(pipeline.model()).thenReturn("gemini-embedding-001");
        when(pipeline.createEmbedding(anyString())).thenReturn(vector);
        UUID familyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        inSourceTransactionAfterCommit(() ->
                aiGateway.upsertSource(SourceType.STORY, sourceId, familyId, null, "Câu chuyện của dòng họ"));
        assertThat(repository.countActiveBySource("STORY", sourceId)).isEqualTo(1);

        inSourceTransactionAfterCommit(() -> aiGateway.removeSource(SourceType.STORY, sourceId));
        assertThat(repository.countActiveBySource("STORY", sourceId)).isZero();
    }

    private void inSourceTransactionAfterCommit(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }));
    }
}
