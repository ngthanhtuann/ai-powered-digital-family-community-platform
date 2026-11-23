package com.familyconnect.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
public class ChatLanguageModel {
    private final RestClient client;
    private final String apiKey;
    private final String model;
    public ChatLanguageModel(@Value("${gemini.api-key:}") String apiKey,
                             @Value("${gemini.chat-model:gemini-3.5-flash-lite}") String model,
                             @Value("${ai.chat.timeout_ms:30000}") int timeoutMs) {
        this.apiKey = apiKey; this.model = model;
        this.client = GeminiHttp.client(GeminiHttp.BASE_URL, timeoutMs);
    }
    public String modelName() { return model; }
    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
    public String answer(String prompt) {
        return generate(prompt, generationConfig(model));
    }

    /** Một lần gọi, ép Gemini trả JSON theo schema (SCRUM-133, BR-AI-18). */
    public String answerJson(String prompt, Map<String, Object> schema) {
        Map<String, Object> config = new java.util.HashMap<>(generationConfig(model));
        config.put("responseMimeType", "application/json");
        config.put("responseSchema", schema);
        return generate(prompt, config);
    }

    private String generate(String prompt, Map<String, Object> generationConfig) {
        try {
            return generateOnce(prompt, generationConfig);
        } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable e) {
            // Gemini hay trả 503 khi quá tải (thử 23/11/2026); thử lại một lần sau 1 giây rồi mới báo AI_001.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return generateOnce(prompt, generationConfig);
        }
    }

    private String generateOnce(String prompt, Map<String, Object> generationConfig) {
        JsonNode response = client.post().uri("/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                        "generationConfig", generationConfig))
                .retrieve().body(JsonNode.class);
        return textOf(response);
    }

    /**
     * Lời đáp bị cắt vì hết token (đánh giá 21/11/2026: "An làm nghề bác") là câu sai, nên coi là lỗi AI_001 thay vì
     * trả nửa câu. Gemini 3 tính cả phần suy nghĩ vào maxOutputTokens.
     */
    static String textOf(JsonNode response) {
        JsonNode candidate = response == null ? null : response.path("candidates").path(0);
        if (candidate != null && "MAX_TOKENS".equals(candidate.path("finishReason").asText())) {
            throw new IllegalStateException("Model response truncated (MAX_TOKENS)");
        }
        // SCRUM-141: Gemini có thể chia câu trả lời thành nhiều part ("Nguyễn Thị Hà có quan hệ là ... của An," rồi phần còn lại),
        // nên ghép mọi part văn bản; bỏ part suy nghĩ (thought=true).
        StringBuilder text = new StringBuilder();
        if (candidate != null) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) {
                    text.append(part.path("text").asText(""));
                }
            }
        }
        if (text.toString().isBlank()) throw new IllegalStateException("Empty model response");
        return text.toString().trim();
    }

    /** 700 từng không đủ khi Gemini 3 suy nghĩ trước khi trả lời (câu trả lời bị cắt). */
    static final int MAX_OUTPUT_TOKENS = 2048;

    /** thinkingLevel chỉ Gemini 3 nhận (model cũ trả 400); Gemini 3 nên để temperature mặc định nên chỉ đặt cho model cũ. */
    static Map<String, Object> generationConfig(String model) {
        return model.startsWith("gemini-3")
                ? Map.of("maxOutputTokens", MAX_OUTPUT_TOKENS, "thinkingConfig", Map.of("thinkingLevel", "low"))
                : Map.of("maxOutputTokens", MAX_OUTPUT_TOKENS, "temperature", 0.1);
    }
}
