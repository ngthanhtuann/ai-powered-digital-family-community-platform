package com.familyconnect.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * BR-AUTH-13: hai quản trị (là hai quản trị duy nhất) khóa nhau cùng lúc thì chỉ một lệnh thành công, hệ thống
 * luôn còn quản trị hoạt động. Không dùng @Transactional để hai request chạy ở hai giao dịch thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> created = new ArrayList<>();

    private UUID admin(String email) throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("email", email, "password", "Matkhau123", "fullName", "Quản trị", "dateOfBirth", "1990-01-01"))));
        UUID id = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'", id);
        created.add(id);
        return id;
    }

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "Matkhau123"))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/data/accessToken").asText();
    }

    @AfterEach
    void cleanUp() {
        // Dữ liệu ghi thật (không rollback): xóa mềm để không ảnh hưởng test khác.
        created.forEach(id -> jdbc.update("update users set deleted_at = now() where id = ?", id));
        created.forEach(id -> jdbc.update("update user_role set deleted_at = now() where user_id = ?", id));
    }

    @Test
    void twoAdminsLockingEachOtherLeavesOneActive() throws Exception {
        // Khóa tạm mọi quản trị khác để hai người này là quản trị hoạt động duy nhất.
        List<UUID> others = jdbc.queryForList("select u.id from users u join user_role ur on ur.user_id = u.id "
                + "join role r on r.id = ur.role_id where r.code = 'SYSTEM_ADMIN' and ur.deleted_at is null "
                + "and u.status = 'ACTIVE' and u.deleted_at is null", UUID.class);
        others.forEach(id -> jdbc.update("update users set status = 'LOCKED' where id = ?", id));
        try {
            String emailA = "conc.a." + UUID.randomUUID() + "@example.com";
            String emailB = "conc.b." + UUID.randomUUID() + "@example.com";
            UUID a = admin(emailA);
            UUID b = admin(emailB);
            String tokenA = token(emailA);
            String tokenB = token(emailB);

            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<Integer> aLocksB = CompletableFuture.supplyAsync(() -> lock(start, tokenA, b));
            CompletableFuture<Integer> bLocksA = CompletableFuture.supplyAsync(() -> lock(start, tokenB, a));
            start.countDown();
            List<Integer> statuses = List.of(aLocksB.get(), bLocksA.get());

            // Chỉ một lệnh thành công; lệnh còn lại nhận 409 ADM_001 hoặc 403 AUTH_003 (người gọi vừa bị khóa).
            assertThat(statuses).containsOnlyOnce(200);
            long active = jdbc.queryForObject("select count(*) from users where id in (?, ?) and status = 'ACTIVE'",
                    Long.class, a, b);
            assertThat(active).isEqualTo(1);
        } finally {
            others.forEach(id -> jdbc.update("update users set status = 'ACTIVE' where id = ?", id));
        }
    }

    private int lock(CountDownLatch start, String token, UUID target) {
        try {
            start.await();
            return mvc.perform(patch("/api/v1/admin/users/" + target).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("status", "LOCKED", "lockReason", "Thử đồng thời"))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
