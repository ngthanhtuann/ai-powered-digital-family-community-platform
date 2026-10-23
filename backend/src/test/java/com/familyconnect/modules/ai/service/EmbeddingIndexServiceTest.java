
package com.familyconnect.modules.ai.service;

import com.familyconnect.modules.ai.dto.PersonEmbeddingInput;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EmbeddingIndexServiceTest {

    @Autowired
    private EmbeddingIndexService embeddingIndexService;

    @Autowired
    private EmbeddingChunkRepository embeddingChunkRepository;

    @Test
    void shouldCreateAndSavePersonEmbedding() {
        UUID familyId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();

        PersonEmbeddingInput person = new PersonEmbeddingInput(
                personId,
                familyId,
                null,
                "Nguyễn Minh Test",
                2004,
                "Thành viên thử nghiệm",
                "Sinh viên",
                false
        );

        embeddingIndexService.indexPerson(person);

        long count = embeddingChunkRepository
                .countActiveBySource(
                        familyId,
                        "PERSON",
                        personId
                );

        assertTrue(count > 0,
                "Expected at least one active embedding chunk");
    }
}
