package com.familyconnect.modules.ai.service;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** SCRUM-104: Gemini không trả lời thì lời gọi embedding phải dừng theo timeout, không treo request. */
class EmbeddingPipelineServiceTest {

    @Test
    void embeddingCallStopsAtTimeoutWhenServerNeverResponds() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = silent.accept()) {
                    Thread.sleep(10_000); // nhận kết nối nhưng không bao giờ trả lời
                } catch (Exception ignored) {
                    // socket đóng khi test kết thúc
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            EmbeddingPipelineService service = new EmbeddingPipelineService(
                    "test-key", "gemini-embedding-001", "http://127.0.0.1:" + silent.getLocalPort(), 300);

            long start = System.nanoTime();
            assertThrows(RuntimeException.class, () -> service.createEmbedding("giỗ tổ"));
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(3_000);
        }
    }
}
