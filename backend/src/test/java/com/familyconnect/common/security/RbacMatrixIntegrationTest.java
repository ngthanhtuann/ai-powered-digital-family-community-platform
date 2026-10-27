package com.familyconnect.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Ma trận phân quyền UC-AUTH-13 (SCRUM-43, test-plan nhóm phân quyền và IDOR): mọi endpoint ghi đã có mà x-roles
 * chỉ gồm BRANCH_ADMIN, SYSTEM_ADMIN. Sai vai trò hoặc gia đình khác nhận PERM_001, chưa xác minh nhận PERM_002,
 * không có token nhận AUTH_002 (thứ tự auth.md mục 4.3). Vai trò BRANCH_ADMIN gắn theo gia đình (member_verification)
 * nên không kiểm bằng @PreAuthorize được; test này giữ cho kiểm tra ở service không bị bỏ sót.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RbacMatrixIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private record Endpoint(HttpMethod method, String url, Object body) {
        @Override
        public String toString() {
            return method + " " + url;
        }
    }

    private String newUser() throws Exception {
        String email = "rbac." + UUID.randomUUID() + "@example.com";
        send(post("/api/v1/users"), null, Map.of("email", email, "password", PASSWORD,
                "fullName", "Người thử", "dateOfBirth", "1990-01-01"));
        return email;
    }

    private String token(String email) throws Exception {
        MockHttpServletResponse res = send(post("/api/v1/auth/login"), null, Map.of("email", email, "password", PASSWORD));
        return json.readTree(res.getContentAsString()).at("/data/accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    private MockHttpServletResponse send(Endpoint e, String token) throws Exception {
        return send(request(e.method(), e.url()), token, e.body());
    }

    private UUID createdId(MockHttpServletResponse res) throws Exception {
        JsonNode body = json.readTree(res.getContentAsString());
        return UUID.fromString(body.at("/data/id").asText());
    }

    private static Map<String, Object> person(String name) {
        return Map.of("fullName", name, "gender", "MALE", "birthDate", "1930-01-01",
                "currentProvince", "Hà Nội", "currentAddress", "12 Phố Huế");
    }

    private String errorCode(MockHttpServletResponse res) throws Exception {
        return json.readTree(res.getContentAsString()).at("/error/code").asText();
    }

    /** Gia đình A có chi, thành viên, ảnh và tài liệu di sản; kèm người dùng ở mỗi vai trò cần thử. */
    private record Fixture(String ownerA, UUID familyA, UUID branchA, UUID personA, UUID mediaA, UUID documentA,
                           UUID storyA, String memberA, String ownerB, String unverified) {
    }

    private UUID uploadHeritageImage(String token, UUID familyId) throws Exception {
        byte[] png = new byte[64];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, png, 0, signature.length);
        MockHttpServletResponse res = mvc.perform(multipart("/api/v1/media")
                .file(new MockMultipartFile("file", "anh.png", "image/png", png))
                .param("ownerType", "HERITAGE").param("ownerId", familyId.toString())
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        return createdId(res);
    }

    private Fixture fixture() throws Exception {
        // Người tạo gia đình A là BRANCH_ADMIN cả gia đình.
        String ownerA = token(newUser());
        UUID familyA = createdId(send(post("/api/v1/families"), ownerA, Map.of("name", "Dòng họ A")));
        UUID branchA = createdId(send(post("/api/v1/families/" + familyA + "/branches"), ownerA, Map.of("name", "Chi 1")));
        UUID personA = createdId(send(post("/api/v1/families/" + familyA + "/persons"), ownerA, person("Cụ tổ")));
        UUID mediaA = uploadHeritageImage(ownerA, familyA);
        UUID documentA = createdId(send(post("/api/v1/heritage/documents"), ownerA,
                Map.of("title", "Gia phả chữ Hán", "mediaId", mediaA.toString())));
        UUID storyA = createdId(send(post("/api/v1/heritage/stories"), ownerA,
                Map.of("title", "Chuyện cụ tổ", "content", "Ngày xưa...")));

        String memberEmail = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "values (?, ?, null, 'MEMBER', 'APPROVED')", userId(memberEmail), familyA);
        String ownerB = token(newUser());
        send(post("/api/v1/families"), ownerB, Map.of("name", "Dòng họ B"));
        return new Fixture(ownerA, familyA, branchA, personA, mediaA, documentA, storyA,
                token(memberEmail), ownerB, token(newUser()));
    }

    /** Không token: 401 AUTH_002; chưa xác minh: 403 PERM_002; trưởng chi gia đình khác (IDOR): 403 PERM_001. */
    private void assertRejectsOutsiders(SoftAssertions soft, Endpoint e, Fixture f) throws Exception {
        MockHttpServletResponse noToken = send(e, null);
        soft.assertThat(noToken.getStatus()).as("không token: %s", e).isEqualTo(401);
        soft.assertThat(errorCode(noToken)).as("không token: %s", e).isEqualTo("AUTH_002");

        MockHttpServletResponse pending = send(e, f.unverified());
        soft.assertThat(pending.getStatus()).as("chưa xác minh: %s", e).isEqualTo(403);
        soft.assertThat(errorCode(pending)).as("chưa xác minh: %s", e).isEqualTo("PERM_002");

        MockHttpServletResponse otherFamily = send(e, f.ownerB());
        soft.assertThat(otherFamily.getStatus()).as("trưởng chi gia đình khác: %s", e).isEqualTo(403);
        soft.assertThat(errorCode(otherFamily)).as("trưởng chi gia đình khác: %s", e).isEqualTo("PERM_001");
    }

    @Test
    void memberEndpointsAllowMemberAndRejectOutsiders() throws Exception {
        Fixture f = fixture();
        String fam = "?familyId=" + f.familyA();
        List<Endpoint> memberLevel = List.of(
                new Endpoint(HttpMethod.GET, "/api/v1/families/" + f.familyA(), null),
                new Endpoint(HttpMethod.GET, "/api/v1/families/" + f.familyA() + "/branches", null),
                new Endpoint(HttpMethod.GET, "/api/v1/families/" + f.familyA() + "/persons", null),
                new Endpoint(HttpMethod.GET, "/api/v1/persons/" + f.personA(), null),
                new Endpoint(HttpMethod.GET, "/api/v1/posts" + fam, null),
                new Endpoint(HttpMethod.POST, "/api/v1/posts" + fam, Map.of("content", "Chào cả nhà", "type", "POST")),
                new Endpoint(HttpMethod.GET, "/api/v1/heritage/documents" + fam, null),
                new Endpoint(HttpMethod.GET, "/api/v1/heritage/documents/" + f.documentA(), null),
                new Endpoint(HttpMethod.GET, "/api/v1/heritage/stories" + fam, null),
                new Endpoint(HttpMethod.GET, "/api/v1/heritage/stories/" + f.storyA(), null),
                new Endpoint(HttpMethod.POST, "/api/v1/heritage/stories" + fam,
                        Map.of("title", "Chuyện mới", "content", "Nội dung")),
                new Endpoint(HttpMethod.GET, "/api/v1/media/" + f.mediaA() + "/content", null));

        SoftAssertions soft = new SoftAssertions();
        for (Endpoint e : memberLevel) {
            assertRejectsOutsiders(soft, e, f);
            soft.assertThat(send(e, f.memberA()).getStatus()).as("MEMBER: %s", e).isBetween(200, 299);
        }
        // Phụ lục A: người chưa xác minh vẫn xem, sửa hồ sơ của mình (không có PERM_002).
        soft.assertThat(send(new Endpoint(HttpMethod.GET, "/api/v1/users/me", null), f.unverified()).getStatus())
                .as("chưa xác minh xem hồ sơ").isEqualTo(200);
        soft.assertThat(send(new Endpoint(HttpMethod.PATCH, "/api/v1/users/me", Map.of("fullName", "Tên mới")),
                f.unverified()).getStatus()).as("chưa xác minh sửa hồ sơ").isEqualTo(200);
        soft.assertAll();
    }

    @Test
    void adminOnlyWriteEndpointsRejectWrongRoleOtherFamilyAndUnverified() throws Exception {
        Fixture f = fixture();
        UUID familyA = f.familyA();
        UUID branchA = f.branchA();
        UUID personA = f.personA();

        List<Endpoint> adminOnly = List.of(
                new Endpoint(HttpMethod.PUT, "/api/v1/families/" + familyA, Map.of("name", "Đổi tên")),
                new Endpoint(HttpMethod.DELETE, "/api/v1/families/" + familyA, null),
                new Endpoint(HttpMethod.POST, "/api/v1/families/" + familyA + "/branches", Map.of("name", "Chi 2")),
                new Endpoint(HttpMethod.PUT, "/api/v1/branches/" + branchA, Map.of("name", "Chi 1 mới")),
                new Endpoint(HttpMethod.DELETE, "/api/v1/branches/" + branchA, null),
                new Endpoint(HttpMethod.POST, "/api/v1/families/" + familyA + "/persons", person("Ông A")),
                new Endpoint(HttpMethod.PUT, "/api/v1/persons/" + personA, person("Cụ tổ sửa")),
                new Endpoint(HttpMethod.DELETE, "/api/v1/persons/" + personA, null),
                new Endpoint(HttpMethod.POST, "/api/v1/posts?familyId=" + familyA,
                        Map.of("content", "Tin tức dòng họ", "type", "NEWS")),
                new Endpoint(HttpMethod.POST, "/api/v1/heritage/documents?familyId=" + familyA,
                        Map.of("title", "Tài liệu mới", "mediaId", f.mediaA().toString())),
                new Endpoint(HttpMethod.PATCH, "/api/v1/heritage/documents/" + f.documentA(), Map.of("title", "Đổi tên")),
                new Endpoint(HttpMethod.DELETE, "/api/v1/heritage/documents/" + f.documentA(), null));

        SoftAssertions soft = new SoftAssertions();
        for (Endpoint e : adminOnly) {
            assertRejectsOutsiders(soft, e, f);
            MockHttpServletResponse member = send(e, f.memberA());
            soft.assertThat(member.getStatus()).as("MEMBER: %s", e).isEqualTo(403);
            soft.assertThat(errorCode(member)).as("MEMBER: %s", e).isEqualTo("PERM_001");
        }
        soft.assertAll();
    }

    @Test
    void rightRoleIsAllowedAndOnlySystemAdminDeletesFamily() throws Exception {
        String ownerA = token(newUser());
        UUID familyA = createdId(send(post("/api/v1/families"), ownerA, Map.of("name", "Dòng họ A")));

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(send(new Endpoint(HttpMethod.PUT, "/api/v1/families/" + familyA, Map.of("name", "Đổi tên")),
                ownerA).getStatus()).as("BRANCH_ADMIN sửa gia đình").isEqualTo(200);
        soft.assertThat(send(new Endpoint(HttpMethod.POST, "/api/v1/families/" + familyA + "/branches",
                Map.of("name", "Chi 1")), ownerA).getStatus()).as("BRANCH_ADMIN tạo chi").isEqualTo(201);
        soft.assertThat(send(new Endpoint(HttpMethod.POST, "/api/v1/posts?familyId=" + familyA,
                Map.of("content", "Tin tức", "type", "NEWS")), ownerA).getStatus()).as("BRANCH_ADMIN đăng tin").isEqualTo(201);

        // Xóa gia đình: x-roles chỉ SYSTEM_ADMIN.
        Endpoint deleteFamily = new Endpoint(HttpMethod.DELETE, "/api/v1/families/" + familyA, null);
        MockHttpServletResponse byOwner = send(deleteFamily, ownerA);
        soft.assertThat(byOwner.getStatus()).as("BRANCH_ADMIN xóa gia đình").isEqualTo(403);
        soft.assertThat(errorCode(byOwner)).isEqualTo("PERM_001");

        String adminEmail = newUser();
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'",
                userId(adminEmail));
        soft.assertThat(send(deleteFamily, token(adminEmail)).getStatus()).as("SYSTEM_ADMIN xóa gia đình").isEqualTo(200);
        soft.assertAll();
    }
}
