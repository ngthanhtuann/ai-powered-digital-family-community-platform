package com.familyconnect.modules.auth;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * SCRUM-50: trang /admin chỉ SYSTEM_ADMIN (UC-AUTH-08, 09), khóa người dùng thì không đăng nhập được,
 * báo cáo nội dung (BR-COM-12) và nội dung bị ẩn không còn trên bảng tin. Nhóm test phân quyền, IDOR, quy ước API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private MockHttpServletResponse send(HttpMethod method, String url, String token, Object body) throws Exception {
        var req = request(method, url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse res) throws Exception {
        return json.readTree(res.getContentAsString(StandardCharsets.UTF_8));
    }

    private String code(MockHttpServletResponse res) throws Exception {
        return body(res).at("/error/code").asText();
    }

    private String newUser(String name) throws Exception {
        String email = "admin.test." + UUID.randomUUID() + "@example.com";
        send(HttpMethod.POST, "/api/v1/users", null, Map.of("email", email, "password", PASSWORD,
                "fullName", name, "dateOfBirth", "1990-01-01"));
        return email;
    }

    private MockHttpServletResponse login(String email) throws Exception {
        return send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD));
    }

    private String token(String email) throws Exception {
        return body(login(email)).at("/data/accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private String newAdmin() throws Exception {
        String email = newUser("Quản trị");
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'",
                userId(email));
        return email;
    }

    private UUID createdId(MockHttpServletResponse res) throws Exception {
        return UUID.fromString(body(res).at("/data/id").asText());
    }

    /** Gia đình A: trưởng chi (người tạo) và một thành viên đã duyệt. */
    private record Family(UUID familyId, String owner, String memberEmail, String member) {
    }

    private Family family(String name) throws Exception {
        String owner = token(newUser("Trưởng " + name));
        UUID familyId = createdId(send(HttpMethod.POST, "/api/v1/families", owner, Map.of("name", name)));
        String memberEmail = newUser("Thành viên " + name);
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "values (?, ?, null, 'MEMBER', 'APPROVED')", userId(memberEmail), familyId);
        return new Family(familyId, owner, memberEmail, token(memberEmail));
    }

    @Test
    void onlySystemAdminCanUseAdminEndpoints() throws Exception {
        Family a = family("Dòng họ A");
        UUID target = userId(a.memberEmail());
        UUID anyId = UUID.randomUUID();
        record Call(HttpMethod method, String url, Object body) {
        }
        List<Call> calls = List.of(
                new Call(HttpMethod.GET, "/api/v1/admin/users", null),
                new Call(HttpMethod.GET, "/api/v1/admin/users/" + target, null),
                new Call(HttpMethod.PATCH, "/api/v1/admin/users/" + target, Map.of("status", "LOCKED", "lockReason", "x")),
                new Call(HttpMethod.PUT, "/api/v1/admin/users/" + target + "/roles", Map.of("roles", List.of("MEMBER"))),
                new Call(HttpMethod.GET, "/api/v1/admin/content-reports", null),
                new Call(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + anyId, Map.of("action", "DISMISS")));

        SoftAssertions soft = new SoftAssertions();
        for (Call c : calls) {
            MockHttpServletResponse noToken = send(c.method(), c.url(), null, c.body());
            soft.assertThat(noToken.getStatus()).as("không token: %s %s", c.method(), c.url()).isEqualTo(401);
            soft.assertThat(code(noToken)).isEqualTo("AUTH_002");
            for (String token : List.of(a.member(), a.owner())) {
                MockHttpServletResponse res = send(c.method(), c.url(), token, c.body());
                soft.assertThat(res.getStatus()).as("MEMBER/BRANCH_ADMIN: %s %s", c.method(), c.url()).isEqualTo(403);
                soft.assertThat(code(res)).as("MEMBER/BRANCH_ADMIN: %s %s", c.method(), c.url()).isEqualTo("PERM_001");
            }
        }
        // Thành viên vẫn hoạt động bình thường: không bị khóa nhầm bởi các lệnh trên.
        soft.assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, target))
                .isEqualTo("ACTIVE");
        soft.assertAll();
    }

    @Test
    void adminSearchesLocksAndUnlocksUser() throws Exception {
        String admin = token(newAdmin());
        String marker = "Khoa" + UUID.randomUUID().toString().substring(0, 8);
        String victimEmail = newUser("Người " + marker);
        String victimToken = token(victimEmail);
        UUID victim = userId(victimEmail);

        MockHttpServletResponse list = send(HttpMethod.GET, "/api/v1/admin/users?q=" + marker.toLowerCase()
                + "&status=ACTIVE&role=MEMBER&page=0&size=10", admin, null);
        assertThat(list.getStatus()).isEqualTo(200);
        assertThat(body(list).at("/meta/total").asLong()).isEqualTo(1);
        assertThat(body(list).at("/data/0/id").asText()).isEqualTo(victim.toString());
        assertThat(body(list).at("/data/0/membership/status").asText()).isEqualTo("NONE");
        assertThat(list.getContentAsString()).doesNotContain("password");
        assertThat(send(HttpMethod.GET, "/api/v1/admin/users?size=101", admin, null).getStatus()).isEqualTo(400);

        String url = "/api/v1/admin/users/" + victim;
        MockHttpServletResponse noReason = send(HttpMethod.PATCH, url, admin, Map.of("status", "LOCKED"));
        assertThat(noReason.getStatus()).isEqualTo(400);
        assertThat(code(noReason)).isEqualTo("VALID_001");

        UUID adminId = UUID.fromString(body(send(HttpMethod.GET, "/api/v1/users/me", admin, null)).at("/data/id").asText());
        MockHttpServletResponse self = send(HttpMethod.PATCH, "/api/v1/admin/users/" + adminId, admin,
                Map.of("status", "LOCKED", "lockReason", "Thử"));
        assertThat(self.getStatus()).isEqualTo(409);
        assertThat(code(self)).isEqualTo("ADM_001");

        MockHttpServletResponse locked = send(HttpMethod.PATCH, url, admin,
                Map.of("status", "LOCKED", "lockReason", "Đăng bài quảng cáo"));
        assertThat(locked.getStatus()).isEqualTo(200);
        assertThat(body(locked).at("/data/status").asText()).isEqualTo("LOCKED");
        assertThat(body(locked).at("/data/lockReason").asText()).isEqualTo("Đăng bài quảng cáo");

        // Token cũ và đăng nhập mới đều nhận 403 AUTH_003; refresh token bị thu hồi.
        MockHttpServletResponse oldToken = send(HttpMethod.GET, "/api/v1/users/me", victimToken, null);
        assertThat(oldToken.getStatus()).isEqualTo(403);
        assertThat(code(oldToken)).isEqualTo("AUTH_003");
        MockHttpServletResponse relogin = login(victimEmail);
        assertThat(relogin.getStatus()).isEqualTo(403);
        assertThat(code(relogin)).isEqualTo("AUTH_003");
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ? and revoked_at is null",
                Long.class, victim)).isZero();

        MockHttpServletResponse unlocked = send(HttpMethod.PATCH, url, admin, Map.of("status", "ACTIVE"));
        assertThat(body(unlocked).at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(body(unlocked).at("/data/lockReason").isNull()).isTrue();
        assertThat(login(victimEmail).getStatus()).isEqualTo(200);

        assertThat(send(HttpMethod.GET, "/api/v1/admin/users/" + UUID.randomUUID(), admin, null).getStatus())
                .isEqualTo(404);
    }

    @Test
    void adminChangesGlobalRoles() throws Exception {
        String adminEmail = newAdmin();
        String admin = token(adminEmail);
        String otherEmail = newUser("Người được nâng quyền");
        String rolesUrl = "/api/v1/admin/users/" + userId(otherEmail) + "/roles";

        MockHttpServletResponse noMember = send(HttpMethod.PUT, rolesUrl, admin, Map.of("roles", List.of("SYSTEM_ADMIN")));
        assertThat(noMember.getStatus()).isEqualTo(400);
        assertThat(code(noMember)).isEqualTo("VALID_001");

        MockHttpServletResponse promoted = send(HttpMethod.PUT, rolesUrl, admin,
                Map.of("roles", List.of("MEMBER", "SYSTEM_ADMIN")));
        assertThat(promoted.getStatus()).isEqualTo(200);
        assertThat(body(promoted).at("/data/roles").toString()).isEqualTo("[\"MEMBER\",\"SYSTEM_ADMIN\"]");
        String other = token(otherEmail);
        assertThat(send(HttpMethod.GET, "/api/v1/admin/users", other, null).getStatus()).isEqualTo(200);

        // Thu quyền có hiệu lực ngay, kể cả với access token cũ còn ghi SYSTEM_ADMIN.
        send(HttpMethod.PUT, rolesUrl, admin, Map.of("roles", List.of("MEMBER")));
        MockHttpServletResponse demoted = send(HttpMethod.GET, "/api/v1/admin/users", other, null);
        assertThat(demoted.getStatus()).isEqualTo(403);
        assertThat(code(demoted)).isEqualTo("PERM_001");

        MockHttpServletResponse self = send(HttpMethod.PUT, "/api/v1/admin/users/" + userId(adminEmail) + "/roles", admin,
                Map.of("roles", List.of("MEMBER")));
        assertThat(self.getStatus()).isEqualTo(409);
        assertThat(code(self)).isEqualTo("ADM_001");
    }

    @Test
    void reportedPostHiddenByAdminDisappearsFromFeed() throws Exception {
        Family a = family("Dòng họ A");
        Family b = family("Dòng họ B");
        String admin = token(newAdmin());
        UUID postId = createdId(send(HttpMethod.POST, "/api/v1/posts", a.owner(),
                Map.of("content", "Mua hàng giảm giá 50% tại cửa hàng X", "type", "POST")));
        Map<String, Object> report = Map.of("targetType", "POST", "targetId", postId.toString(),
                "reason", "Bài đăng quảng cáo");

        // Người ngoài gia đình, quản trị, người chưa xác minh không báo cáo được; nội dung không có trả 404.
        assertThat(code(send(HttpMethod.POST, "/api/v1/content-reports", b.owner(), report))).isEqualTo("PERM_001");
        assertThat(code(send(HttpMethod.POST, "/api/v1/content-reports", admin, report))).isEqualTo("PERM_001");
        assertThat(code(send(HttpMethod.POST, "/api/v1/content-reports", token(newUser("Chưa duyệt")), report)))
                .isEqualTo("PERM_002");
        assertThat(send(HttpMethod.POST, "/api/v1/content-reports", a.member(), Map.of("targetType", "POST",
                "targetId", UUID.randomUUID().toString(), "reason", "x")).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.POST, "/api/v1/content-reports", a.member(), Map.of("targetType", "POST",
                "targetId", postId.toString(), "reason", " ")).getStatus()).isEqualTo(400);

        MockHttpServletResponse created = send(HttpMethod.POST, "/api/v1/content-reports", a.member(), report);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(body(created).at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(body(created).at("/data/contentSnippet").asText()).isEqualTo("Mua hàng giảm giá 50% tại cửa hàng X");
        UUID reportId = createdId(created);
        MockHttpServletResponse duplicate = send(HttpMethod.POST, "/api/v1/content-reports", a.member(), report);
        assertThat(duplicate.getStatus()).isEqualTo(409);
        assertThat(code(duplicate)).isEqualTo("CONFLICT_001");

        MockHttpServletResponse queue = send(HttpMethod.GET, "/api/v1/admin/content-reports?targetType=POST&size=100",
                admin, null);
        assertThat(queue.getStatus()).isEqualTo(200);
        assertThat(queue.getContentAsString()).contains(reportId.toString());

        MockHttpServletResponse hidden = send(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + reportId, admin,
                Map.of("action", "HIDE_CONTENT"));
        assertThat(hidden.getStatus()).isEqualTo(200);
        assertThat(body(hidden).at("/data/status").asText()).isEqualTo("HIDDEN");

        // Nội dung bị ẩn không hiện trên bảng tin, không xóa cứng.
        String feed = send(HttpMethod.GET, "/api/v1/posts?familyId=" + a.familyId(), a.member(), null).getContentAsString();
        assertThat(feed).doesNotContain(postId.toString());
        assertThat(send(HttpMethod.GET, "/api/v1/posts/" + postId, a.member(), null).getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from post where id = ? and hidden_at is not null "
                + "and deleted_at is null", Long.class, postId)).isEqualTo(1);

        MockHttpServletResponse again = send(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + reportId, admin,
                Map.of("action", "DISMISS"));
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(code(again)).isEqualTo("ADM_002");
        assertThat(send(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + UUID.randomUUID(), admin,
                Map.of("action", "DISMISS")).getStatus()).isEqualTo(404);
        assertThat(body(send(HttpMethod.GET, "/api/v1/admin/content-reports?status=HIDDEN&size=100", admin, null))
                .at("/meta/total").asLong()).isPositive();
    }

    @Test
    void dismissedCommentReportKeepsComment() throws Exception {
        Family a = family("Dòng họ C");
        String admin = token(newAdmin());
        UUID postId = createdId(send(HttpMethod.POST, "/api/v1/posts", a.owner(), Map.of("content", "Họp họ", "type", "POST")));
        UUID commentId = createdId(send(HttpMethod.POST, "/api/v1/posts/" + postId + "/comments", a.owner(),
                Map.of("content", "Nhớ đến đúng giờ")));
        UUID reportId = createdId(send(HttpMethod.POST, "/api/v1/content-reports", a.member(),
                Map.of("targetType", "COMMENT", "targetId", commentId.toString(), "reason", "Nhầm")));

        MockHttpServletResponse dismissed = send(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + reportId, admin,
                Map.of("action", "DISMISS"));
        assertThat(dismissed.getStatus()).isEqualTo(200);
        assertThat(body(dismissed).at("/data/status").asText()).isEqualTo("DISMISSED");
        assertThat(send(HttpMethod.GET, "/api/v1/posts/" + postId + "/comments", a.member(), null).getContentAsString())
                .contains(commentId.toString());
    }

    private UUID me(String token) throws Exception {
        return UUID.fromString(body(send(HttpMethod.GET, "/api/v1/users/me", token, null)).at("/data/id").asText());
    }

    @Test
    void familyMemberCannotBecomeSystemAdmin() throws Exception {
        Family a = family("Dòng họ D");
        String admin = token(newAdmin());
        MockHttpServletResponse res = send(HttpMethod.PUT, "/api/v1/admin/users/" + userId(a.memberEmail()) + "/roles",
                admin, Map.of("roles", List.of("MEMBER", "SYSTEM_ADMIN")));
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(code(res)).isEqualTo("VALID_001");
        assertThat(jdbc.queryForObject("select count(*) from user_role ur join role r on r.id = ur.role_id "
                + "where ur.user_id = ? and r.code = 'SYSTEM_ADMIN' and ur.deleted_at is null", Long.class,
                userId(a.memberEmail()))).isZero();
    }

    @Test
    void adminAssignsAndMovesMembership() throws Exception {
        Family a = family("Dòng họ E");
        Family b = family("Dòng họ F");
        String admin = token(newAdmin());
        UUID branchA = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.familyId() + "/branches", a.owner(),
                Map.of("name", "Chi hai")));
        String loneEmail = newUser("Người mới");
        String url = "/api/v1/admin/users/" + userId(loneEmail) + "/membership";

        MockHttpServletResponse assigned = send(HttpMethod.PUT, url, admin,
                Map.of("familyId", a.familyId().toString(), "branchId", branchA.toString(), "role", "BRANCH_ADMIN"));
        assertThat(assigned.getStatus()).isEqualTo(200);
        assertThat(body(assigned).at("/data/membership/status").asText()).isEqualTo("APPROVED");
        assertThat(body(assigned).at("/data/membership/role").asText()).isEqualTo("BRANCH_ADMIN");
        assertThat(body(assigned).at("/data/membership/branchName").asText()).isEqualTo("Chi hai");

        // Chuyển sang gia đình khác: bản ghi cũ REMOVED, chỉ còn một bản ghi hiệu lực.
        MockHttpServletResponse moved = send(HttpMethod.PUT, url, admin,
                Map.of("familyId", b.familyId().toString(), "role", "MEMBER"));
        assertThat(body(moved).at("/data/membership/familyId").asText()).isEqualTo(b.familyId().toString());
        assertThat(jdbc.queryForList("select status from member_verification where user_id = ? order by created_at",
                String.class, userId(loneEmail))).containsExactly("REMOVED", "APPROVED");

        assertThat(send(HttpMethod.PUT, url, admin, Map.of("familyId", UUID.randomUUID().toString(), "role", "MEMBER"))
                .getStatus()).isEqualTo(404);
        MockHttpServletResponse toAdmin = send(HttpMethod.PUT, "/api/v1/admin/users/" + me(admin) + "/membership", admin,
                Map.of("familyId", a.familyId().toString(), "role", "MEMBER"));
        assertThat(toAdmin.getStatus()).isEqualTo(400);
        assertThat(code(toAdmin)).isEqualTo("VALID_001");
    }

    @Test
    void searchIgnoresVietnameseAccents() throws Exception {
        String admin = token(newAdmin());
        String marker = UUID.randomUUID().toString().substring(0, 8);
        UUID id = userId(newUser("Đặng Thị Ngọc Ánh " + marker));
        String found = send(HttpMethod.GET, "/api/v1/admin/users?q=dang thi ngoc anh " + marker, admin, null)
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(found).contains(id.toString());
    }

    @Test
    void queueGroupsReportsAndAdminCanRestoreHiddenContent() throws Exception {
        Family a = family("Dòng họ G");
        String admin = token(newAdmin());
        String secondEmail = newUser("Thành viên hai");
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "values (?, ?, null, 'MEMBER', 'APPROVED')", userId(secondEmail), a.familyId());
        UUID postId = createdId(send(HttpMethod.POST, "/api/v1/posts", a.owner(),
                Map.of("content", "Bài cần xem xét", "type", "POST")));
        Map<String, Object> report = Map.of("targetType", "POST", "targetId", postId.toString(), "reason", "Spam");
        UUID first = createdId(send(HttpMethod.POST, "/api/v1/content-reports", a.member(), report));
        send(HttpMethod.POST, "/api/v1/content-reports", token(secondEmail), report);

        JsonNode queue = body(send(HttpMethod.GET, "/api/v1/admin/content-reports?size=100", admin, null));
        JsonNode row = null;
        for (JsonNode r : queue.get("data")) {
            if (r.get("targetId").asText().equals(postId.toString())) {
                assertThat(row).as("mỗi nội dung một dòng").isNull();
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("reportCount").asInt()).isEqualTo(2);
        assertThat(row.get("contentOwnerName").asText()).isEqualTo("Trưởng Dòng họ G");
        assertThat(row.get("familyName").asText()).isEqualTo("Dòng họ G");

        MockHttpServletResponse hidden = send(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + first, admin,
                Map.of("action", "HIDE_CONTENT"));
        assertThat(body(hidden).at("/data/reportCount").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForList("select status from content_report where target_id = ?", String.class, postId))
                .containsOnly("HIDDEN");
        assertThat(send(HttpMethod.GET, "/api/v1/posts/" + postId, a.member(), null).getStatus()).isEqualTo(404);

        MockHttpServletResponse restored = send(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + first, admin,
                Map.of("action", "RESTORE_CONTENT"));
        assertThat(restored.getStatus()).isEqualTo(200);
        assertThat(body(restored).at("/data/status").asText()).isEqualTo("DISMISSED");
        assertThat(send(HttpMethod.GET, "/api/v1/posts/" + postId, a.member(), null).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForList("select status from content_report where target_id = ?", String.class, postId))
                .containsOnly("DISMISSED");
        MockHttpServletResponse again = send(HttpMethod.PATCH, "/api/v1/admin/content-reports/" + first, admin,
                Map.of("action", "RESTORE_CONTENT"));
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(code(again)).isEqualTo("ADM_002");
    }

    @Test
    void familyHeadAppointsBranchHead() throws Exception {
        Family a = family("Dòng họ H");
        Family b = family("Dòng họ I");
        String admin = token(newAdmin());
        UUID branch = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.familyId() + "/branches", a.owner(),
                Map.of("name", "Chi ba")));
        String base = "/api/v1/families/" + a.familyId() + "/accounts";
        UUID member = userId(a.memberEmail());
        UUID owner = me(a.owner());

        MockHttpServletResponse list = send(HttpMethod.GET, base, a.owner(), null);
        assertThat(list.getStatus()).isEqualTo(200);
        assertThat(body(list).at("/meta/total").asLong()).isEqualTo(2);
        assertThat(list.getContentAsString()).doesNotContain("email");

        // Thành viên, trưởng họ dòng họ khác không xem, không bổ nhiệm được.
        assertThat(code(send(HttpMethod.GET, base, a.member(), null))).isEqualTo("PERM_001");
        assertThat(code(send(HttpMethod.GET, base, b.owner(), null))).isEqualTo("PERM_001");

        MockHttpServletResponse appointed = send(HttpMethod.PUT, base + "/" + member, a.owner(),
                Map.of("role", "BRANCH_ADMIN", "branchId", branch.toString()));
        assertThat(appointed.getStatus()).isEqualTo(200);
        assertThat(body(appointed).at("/data/branchName").asText()).isEqualTo("Chi ba");
        assertThat(body(send(HttpMethod.GET, "/api/v1/users/me/membership", a.member(), null)).at("/data/role").asText())
                .isEqualTo("BRANCH_ADMIN");
        // Trưởng chi có gán chi không bổ nhiệm được.
        assertThat(code(send(HttpMethod.GET, base, a.member(), null))).isEqualTo("PERM_001");

        assertThat(send(HttpMethod.PUT, base + "/" + member, a.owner(),
                Map.of("role", "BRANCH_ADMIN", "branchId", UUID.randomUUID().toString())).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.PUT, base + "/" + userId(b.memberEmail()), a.owner(), Map.of("role", "MEMBER"))
                .getStatus()).isEqualTo(404);
        assertThat(code(send(HttpMethod.PUT, base + "/" + owner, a.owner(), Map.of("role", "MEMBER"))))
                .isEqualTo("ADM_001");
        // Quản trị thu quyền trưởng họ duy nhất: ADM_001; khi đã có trưởng họ thứ hai thì được.
        assertThat(code(send(HttpMethod.PUT, base + "/" + owner, admin, Map.of("role", "MEMBER")))).isEqualTo("ADM_001");
        send(HttpMethod.PUT, base + "/" + member, a.owner(), Map.of("role", "BRANCH_ADMIN"));
        assertThat(send(HttpMethod.PUT, base + "/" + owner, admin, Map.of("role", "MEMBER")).getStatus()).isEqualTo(200);
    }

    @Test
    void reportingIsRateLimitedPerHour() throws Exception {
        Family a = family("Dòng họ K");
        for (int i = 0; i < 20; i++) {
            UUID post = createdId(send(HttpMethod.POST, "/api/v1/posts", a.owner(), Map.of("content", "Bài " + i, "type", "POST")));
            assertThat(send(HttpMethod.POST, "/api/v1/content-reports", a.member(),
                    Map.of("targetType", "POST", "targetId", post.toString(), "reason", "Spam")).getStatus()).isEqualTo(201);
        }
        UUID extra = createdId(send(HttpMethod.POST, "/api/v1/posts", a.owner(), Map.of("content", "Bài thêm", "type", "POST")));
        MockHttpServletResponse limited = send(HttpMethod.POST, "/api/v1/content-reports", a.member(),
                Map.of("targetType", "POST", "targetId", extra.toString(), "reason", "Spam"));
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(code(limited)).isEqualTo("RATE_001");
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1L, 3600L);
    }
}
