package com.familyconnect.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.List;
import java.util.Map;

@Service
public class ChatLanguageModel {
    private final RestClient client;
    private final String apiKey;
    private final String model;
    public ChatLanguageModel(@Value("${gemini.api-key:}") String apiKey,
                             @Value("${gemini.chat-model:gemini-2.5-flash}") String model,
                             @Value("${ai.chat.timeout_ms:30000}") int timeoutMs) {
        this.apiKey = apiKey; this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(5000, timeoutMs));
        factory.setReadTimeout(timeoutMs);
        this.client = RestClient.builder().requestFactory(factory)
                .baseUrl("https://generativelanguage.googleapis.com/v1beta").build();
    }
    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
    public String answer(String prompt) {
        JsonNode response = client.post().uri("/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                        "generationConfig", Map.of("temperature", 0.1, "maxOutputTokens", 700)))
                .retrieve().body(JsonNode.class);
        String text = response == null ? "" : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (text.isBlank()) throw new IllegalStateException("Empty model response");
        return text.trim();
    }
}
