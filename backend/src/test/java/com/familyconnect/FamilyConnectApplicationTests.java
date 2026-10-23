package com.familyconnect;

import static org.assertj.core.api.Assertions.assertThat;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.service.ConfiguredGenealogyQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Kiểm tra khung hệ thống: ứng dụng khởi động được (kể cả Flyway migration)
 * và endpoint /actuator/health trả về 200.
 * Cần PostgreSQL (pgvector) đang chạy: docker compose up -d db
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FamilyConnectApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GenealogyQuery genealogyQuery;

    @Test
    void contextLoads() {
        assertThat(genealogyQuery).isInstanceOf(ConfiguredGenealogyQuery.class);
    }

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }
}
