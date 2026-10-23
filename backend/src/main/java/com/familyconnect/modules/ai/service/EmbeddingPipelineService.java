package com.familyconnect.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Gọi Gemini Embedding (adr-ai-spike.md, ai-rag.md): gemini-embedding-001, outputDimensionality = 768. */
@Service
public class EmbeddingPipelineService {

    static final int DIMENSIONS = 768;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public EmbeddingPipelineService(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.embedding-model:gemini-embedding-001}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    /** Chưa đặt GEMINI_API_KEY (máy dev, CI) thì không gọi Gemini. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    public float[] createEmbedding(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content must not be blank");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY chưa được cấu hình");
        }

        // Khóa gửi ở header, không đặt trên URL để không lộ qua log.
        JsonNode response = restClient.post()
                .uri("/models/{model}:embedContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", "models/" + model,
                        "content", Map.of("parts", List.of(Map.of("text", content))),
                        "outputDimensionality", DIMENSIONS))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("embedding").has("values")) {
            throw new IllegalStateException("Gemini returned an invalid embedding response");
        }
        JsonNode values = response.get("embedding").get("values");
        if (values.size() != DIMENSIONS) {
            throw new IllegalStateException("Expected " + DIMENSIONS + " dimensions but received " + values.size());
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = (float) values.get(i).asDouble();
        }
        return vector;
    }
}
