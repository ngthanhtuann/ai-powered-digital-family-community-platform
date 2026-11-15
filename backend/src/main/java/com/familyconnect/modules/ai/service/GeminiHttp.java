package com.familyconnect.modules.ai.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client dùng chung cho các lời gọi Gemini: luôn có thời gian chờ (BR-AI-11) để request không treo. */
final class GeminiHttp {
    static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private GeminiHttp() {
    }

    /** Kết nối tối đa 5 giây (hoặc ít hơn nếu timeout nhỏ hơn), đọc tối đa {@code timeoutMs}. */
    static RestClient client(String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(5000, timeoutMs));
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }
}
