package com.familyconnect.modules.ai.service;

import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.ai.dto.EmbeddingChunkData;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cài đặt AiGateway (ai.md mục 6.1): module nguồn gửi nội dung đã lọc (BR-AI-07, BR-AI-14),
 * module ai chia đoạn, tạo embedding và lưu vào ai_embedding_chunk.
 * Lỗi của AI chỉ ghi log, không làm hỏng thao tác của module nguồn.
 */
@Service
public class EmbeddingIndexService implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIndexService.class);

    private final EmbeddingPipelineService embeddingPipelineService;
    private final EmbeddingChunkRepository embeddingChunkRepository;
    private final ContentHashService contentHashService;
    private final TextChunkingService textChunkingService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public EmbeddingIndexService(EmbeddingPipelineService embeddingPipelineService,
                                 EmbeddingChunkRepository embeddingChunkRepository,
                                 ContentHashService contentHashService,
                                 TextChunkingService textChunkingService,
                                 PlatformTransactionManager transactionManager,
                                 ObjectMapper objectMapper) {
        this.embeddingPipelineService = embeddingPipelineService;
        this.embeddingChunkRepository = embeddingChunkRepository;
        this.contentHashService = contentHashService;
        this.textChunkingService = textChunkingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsertSource(SourceType type, UUID sourceId, UUID familyId, UUID branchId, String content) {
        try {
            index(type, sourceId, familyId, branchId, content);
        } catch (RuntimeException ex) {
            // Không ghi nội dung vào log (privacy.md); lần sửa nguồn sau sẽ lập chỉ mục lại.
            log.warn("Không lập được chỉ mục AI: type={}, sourceId={}", type, sourceId, ex);
        }
    }

    @Override
    public void removeSource(SourceType type, UUID sourceId) {
        try {
            embeddingChunkRepository.softDeleteActiveBySource(type.name(), sourceId);
        } catch (RuntimeException ex) {
            log.warn("Không xóa được chỉ mục AI: type={}, sourceId={}", type, sourceId, ex);
        }
    }

    void index(SourceType type, UUID sourceId, UUID familyId, UUID branchId, String content) {
        if (type == null || sourceId == null || familyId == null) {
            throw new IllegalArgumentException("type, sourceId và familyId là bắt buộc");
        }
        List<String> chunks = textChunkingService.split(content);
        if (chunks.isEmpty()) {
            embeddingChunkRepository.softDeleteActiveBySource(type.name(), sourceId);
            return;
        }
        List<String> hashes = chunks.stream().map(contentHashService::hash).toList();
        if (hashes.equals(embeddingChunkRepository.findActiveHashes(type.name(), sourceId))) {
            return; // nội dung không đổi, không gọi lại Gemini
        }
        if (!embeddingPipelineService.isConfigured()) {
            log.info("Bỏ qua lập chỉ mục AI vì chưa cấu hình GEMINI_API_KEY: type={}, sourceId={}", type, sourceId);
            return;
        }

        // Gọi Gemini trước, ngoài giao dịch, để không giữ kết nối DB trong lúc chờ mạng.
        List<float[]> embeddings = new ArrayList<>();
        for (String chunk : chunks) {
            embeddings.add(embeddingPipelineService.createEmbedding(chunk));
        }
        String metadata = metadata(chunks.size());
        transactionTemplate.executeWithoutResult(status -> {
            embeddingChunkRepository.softDeleteActiveBySource(type.name(), sourceId);
            for (int i = 0; i < chunks.size(); i++) {
                embeddingChunkRepository.save(new EmbeddingChunkData(familyId, branchId, type.name(), sourceId, i,
                        chunks.get(i), hashes.get(i), embeddings.get(i)), metadata);
            }
        });
    }

    private String metadata(int chunkCount) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("model", embeddingPipelineService.model(), "chunkCount", chunkCount));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
