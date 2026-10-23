
package com.familyconnect.modules.ai.service;

import com.familyconnect.modules.ai.dto.ContentEmbeddingInput;
import com.familyconnect.modules.ai.dto.EmbeddingChunkData;
import com.familyconnect.modules.ai.dto.PersonEmbeddingInput;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EmbeddingIndexService {

    private final EmbeddingPipelineService embeddingPipelineService;
    private final EmbeddingChunkRepository embeddingChunkRepository;
    private final ContentHashService contentHashService;
    private final PersonTextBuilder personTextBuilder;
    private final TextChunkingService textChunkingService;

    public EmbeddingIndexService(
            EmbeddingPipelineService embeddingPipelineService,
            EmbeddingChunkRepository embeddingChunkRepository,
            ContentHashService contentHashService,
            PersonTextBuilder personTextBuilder,
            TextChunkingService textChunkingService) {

        this.embeddingPipelineService = embeddingPipelineService;
        this.embeddingChunkRepository = embeddingChunkRepository;
        this.contentHashService = contentHashService;
        this.personTextBuilder = personTextBuilder;
        this.textChunkingService = textChunkingService;
    }

    @Transactional
    public void indexPerson(PersonEmbeddingInput person) {
        if (person == null || person.personId() == null
                || person.familyId() == null) {
            throw new IllegalArgumentException(
                    "Person ID and family ID are required");
        }

        String content = personTextBuilder.build(person);

        indexChunks(
                person.familyId(),
                person.branchId(),
                "PERSON",
                person.personId(),
                List.of(content)
        );
    }

    @Transactional
    public void indexContent(ContentEmbeddingInput input) {
        if (input == null || input.sourceId() == null
                || input.familyId() == null) {
            throw new IllegalArgumentException(
                    "Source ID and family ID are required");
        }

        String sourceType = input.sourceType();

        if (sourceType == null
                || !(sourceType.equals("POST")
                || sourceType.equals("STORY"))) {
            throw new IllegalArgumentException(
                    "Source type must be POST or STORY");
        }

        String title = input.title() == null
                ? "" : input.title().trim();

        String body = input.content() == null
                ? "" : input.content().trim();

        String content = (title.isBlank() ? "" : title + ". ")
                + body;

        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "Content must not be blank");
        }

        List<String> chunks = textChunkingService.split(content);

        indexChunks(
                input.familyId(),
                input.branchId(),
                sourceType,
                input.sourceId(),
                chunks
        );
    }

    private void indexChunks(
            UUID familyId,
            UUID branchId,
            String sourceType,
            UUID sourceId,
            List<String> chunks) {

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one chunk is required");
        }

        embeddingChunkRepository.softDeleteActiveBySource(
                familyId, sourceType, sourceId);

        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            String contentHash = contentHashService.hash(content);
            float[] embedding =
                    embeddingPipelineService.createEmbedding(content);

            EmbeddingChunkData chunk = new EmbeddingChunkData(
                    familyId,
                    branchId,
                    sourceType,
                    sourceId,
                    i,
                    content,
                    contentHash,
                    embedding
            );

            embeddingChunkRepository.save(chunk);
        }
    }
}
