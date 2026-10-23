
package com.familyconnect.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingPipelineService {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public EmbeddingPipelineService(
            @Value("${gemini.api-key:AQ.Ab8RN6LhqAig1T79YHAtuvXazhraAqNB0OGzQRyqGJeeaNv05w}") String apiKey,
            @Value("${gemini.embedding-model:gemini-embedding-001}") String model) {

        this.apiKey = apiKey;
        this.model = model;

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    public float[] createEmbedding(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Content must not be blank");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is not configured");
        }

        JsonNode response = restClient.post()
                .uri("/models/{model}:embedContent?key={key}",
                        model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of(
                        "model", "models/" + model,
                        "content", java.util.Map.of(
                                "parts", java.util.List.of(
                                        java.util.Map.of(
                                                "text", content))),
                        "outputDimensionality", 768))
                .retrieve()
                .body(JsonNode.class);

        if (response == null
                || !response.has("embedding")
                || !response.get("embedding").has("values")) {
            throw new IllegalStateException(
                    "Gemini returned an invalid embedding response");
        }

        JsonNode values = response.get("embedding").get("values");

        if (values.size() != 768) {
            throw new IllegalStateException(
                    "Expected 768 dimensions but received "
                            + values.size());
        }

        float[] vector = new float[values.size()];

        for (int i = 0; i < values.size(); i++) {
            vector[i] = (float) values.get(i).asDouble();
        }

        return vector;
    }
}
