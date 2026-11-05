package com.familyconnect.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * SCRUM-100: xin tham gia gia đình và duyệt (UC-AUTH-06, 07; BR-AUTH-08 đến 11). Nhóm test phân quyền, IDOR,
 * quy ước API: phạm vi chi của trưởng chi, gia đình khác, mã AUTH_005, AUTH_006, PERM_001, PERM_002.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JoinRequestApiIntegrationTest {

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
        String email = "join." + UUID.randomUUID() + "@example.com";
        send(HttpMethod.POST, "/api/v1/users", null, Map.of("email", email, "password", PASSWORD,
                "fullName", name, "dateOfBirth", "1990-01-01"));
        return email;
    }

    private String token(String email) throws Exception {
        return body(send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD)))
                .at("/data/accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private UUID createdId(MockHttpServletResponse res) throws Exception {
        return UUID.fromString(body(res).at("/data/id").asText());
    }

    private record Family(UUID id, String head) {
    }

    private Family family(String name) throws Exception {
        String head = token(newUser("Trưởng " + name));
        return new Family(createdId(send(HttpMethod.POST, "/api/v1/families", head, Map.of("name", name))), head);
    }

    /** code là mã gia đình hoặc mã chi (BR-AUTH-09). */
    private MockHttpServletResponse join(String token, UUID code) throws Exception {
        return send(HttpMethod.POST, "/api/v1/join-requests", token, Map.of("code", code.toString(), "message", "Con ông Bình"));
    }

    @Test
    void applicantJoinsAndHeadApproves() throws Exception {
        Family a = family("Dòng họ A");
        String email = newUser("Người xin");
        String applicant = token(email);

        MockHttpServletResponse created = join(applicant, a.id());
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(body(created).at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(body(created).at("/data/applicantEmail").asText()).isEqualTo(email);
        UUID requestId = createdId(created);
        JsonNode membership = body(send(HttpMethod.GET, "/api/v1/users/me/membership", applicant, null)).get("data");
        assertThat(membership.get("status").asText()).isEqualTo("PENDING");
        assertThat(membership.get("joinRequestId").asText()).isEqualTo(requestId.toString());

        MockHttpServletResponse again = join(applicant, a.id());
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(code(again)).isEqualTo("AUTH_005");
        // Chưa duyệt: dữ liệu gia đình trả PERM_002.
        assertThat(code(send(HttpMethod.GET, "/api/v1/families/" + a.id(), applicant, null))).isEqualTo("PERM_002");

        MockHttpServletResponse list = send(HttpMethod.GET, "/api/v1/join-requests", a.head(), null);
        assertThat(list.getStatus()).isEqualTo(200);
        assertThat(body(list).at("/meta/total").asLong()).isEqualTo(1);
        assertThat(body(list).at("/data/0/message").asText()).isEqualTo("Con ông Bình");

        MockHttpServletResponse approved = send(HttpMethod.PATCH, "/api/v1/join-requests/" + requestId, a.head(),
                Map.of("status", "APPROVED"));
        assertThat(approved.getStatus()).isEqualTo(200);
        assertThat(body(approved).at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(body(approved).at("/data/reviewedBy").asText()).isNotBlank();
        assertThat(send(HttpMethod.GET, "/api/v1/families/" + a.id(), applicant, null).getStatus()).isEqualTo(200);

        MockHttpServletResponse twice = send(HttpMethod.PATCH, "/api/v1/join-requests/" + requestId, a.head(),
                Map.of("status", "REJECTED"));
        assertThat(twice.getStatus()).isEqualTo(409);
        assertThat(code(twice)).isEqualTo("AUTH_006");
        assertThat(code(join(applicant, a.id()))).isEqualTo("AUTH_005");
    }

    @Test
    void createRejectsAdminsMissingFamilyAndBadBranch() throws Exception {
        Family a = family("Dòng họ B");
        String adminEmail = newUser("Quản trị");
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'",
                userId(adminEmail));
        assertThat(code(join(token(adminEmail), a.id()))).isEqualTo("PERM_001");
        String applicant = token(newUser("Người xin"));
        MockHttpServletResponse wrong = join(applicant, UUID.randomUUID());
        assertThat(wrong.getStatus()).isEqualTo(404);
        assertThat(code(wrong)).isEqualTo("NOT_FOUND_001");
        assertThat(send(HttpMethod.POST, "/api/v1/join-requests", applicant, Map.of("message", "thiếu mã"))
                .getStatus()).isEqualTo(400);
    }

    @Test
    void branchHeadOnlyReviewsRequestsInBranchSubtree() throws Exception {
        Family a = family("Dòng họ C");
        Family b = family("Dòng họ D");
        UUID branch1 = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.id() + "/branches", a.head(),
                Map.of("name", "Chi một")));
        UUID branch1Child = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.id() + "/branches", a.head(),
                Map.of("name", "Chi một con", "parentBranchId", branch1.toString())));
        UUID branch2 = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.id() + "/branches", a.head(),
                Map.of("name", "Chi hai")));
        String branchHeadEmail = newUser("Trưởng chi một");
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "values (?, ?, ?, 'BRANCH_ADMIN', 'APPROVED')", userId(branchHeadEmail), a.id(), branch1);
        String branchHead = token(branchHeadEmail);

        UUID inChild = createdId(join(token(newUser("Xin chi con")), branch1Child));
        UUID inOther = createdId(join(token(newUser("Xin chi hai")), branch2));
        UUID noBranch = createdId(join(token(newUser("Xin cả họ")), a.id()));

        assertThat(jdbc.queryForObject("select family_id from member_verification where id = ?", UUID.class, inChild))
                .isEqualTo(a.id());
        assertThat(jdbc.queryForObject("select branch_id from member_verification where id = ?", UUID.class, inChild))
                .isEqualTo(branch1Child);
        assertThat(code(send(HttpMethod.PATCH, "/api/v1/join-requests/" + inChild, branchHead,
                Map.of("status", "APPROVED", "branchId", branch2.toString())))).isEqualTo("PERM_001");
        String seen = send(HttpMethod.GET, "/api/v1/join-requests", branchHead, null).getContentAsString();
        assertThat(seen).contains(inChild.toString()).doesNotContain(inOther.toString(), noBranch.toString());
        assertThat(send(HttpMethod.PATCH, "/api/v1/join-requests/" + inChild, branchHead, Map.of("status", "APPROVED"))
                .getStatus()).isEqualTo(200);
        assertThat(code(send(HttpMethod.PATCH, "/api/v1/join-requests/" + inOther, branchHead,
                Map.of("status", "APPROVED")))).isEqualTo("PERM_001");
        assertThat(code(send(HttpMethod.PATCH, "/api/v1/join-requests/" + noBranch, branchHead,
                Map.of("status", "APPROVED")))).isEqualTo("PERM_001");

        // Gia đình khác (IDOR): không thấy, không duyệt được.
        assertThat(body(send(HttpMethod.GET, "/api/v1/join-requests", b.head(), null)).at("/meta/total").asLong()).isZero();
        assertThat(code(send(HttpMethod.PATCH, "/api/v1/join-requests/" + noBranch, b.head(),
                Map.of("status", "APPROVED")))).isEqualTo("PERM_001");
        // Thành viên thường và người chưa xác minh.
        String memberEmail = newUser("Thành viên");
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "values (?, ?, null, 'MEMBER', 'APPROVED')", userId(memberEmail), a.id());
        assertThat(code(send(HttpMethod.GET, "/api/v1/join-requests", token(memberEmail), null))).isEqualTo("PERM_001");
        assertThat(code(send(HttpMethod.GET, "/api/v1/join-requests", token(newUser("Lạ")), null))).isEqualTo("PERM_002");
        assertThat(send(HttpMethod.GET, "/api/v1/join-requests", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void rejectCancelAndLinkPerson() throws Exception {
        Family a = family("Dòng họ E");
        String applicant = token(newUser("Người xin"));
        UUID first = createdId(join(applicant, a.id()));

        MockHttpServletResponse rejected = send(HttpMethod.PATCH, "/api/v1/join-requests/" + first, a.head(),
                Map.of("status", "REJECTED", "rejectReason", "Chưa rõ quan hệ"));
        assertThat(body(rejected).at("/data/status").asText()).isEqualTo("REJECTED");
        JsonNode membership = body(send(HttpMethod.GET, "/api/v1/users/me/membership", applicant, null)).get("data");
        assertThat(membership.get("status").asText()).isEqualTo("REJECTED");
        assertThat(membership.get("rejectReason").asText()).isEqualTo("Chưa rõ quan hệ");

        // Bị từ chối thì gửi lại được; hủy yêu cầu của mình; người khác hủy nhận PERM_001.
        UUID second = createdId(join(applicant, a.id()));
        String stranger = token(newUser("Người khác"));
        assertThat(code(send(HttpMethod.DELETE, "/api/v1/join-requests/" + second, stranger, null))).isEqualTo("PERM_001");
        MockHttpServletResponse cancelled = send(HttpMethod.DELETE, "/api/v1/join-requests/" + second, applicant, null);
        assertThat(cancelled.getStatus()).isEqualTo(200);
        assertThat(body(cancelled).get("data").isNull()).isTrue();
        assertThat(code(send(HttpMethod.DELETE, "/api/v1/join-requests/" + second, applicant, null))).isEqualTo("AUTH_006");
        assertThat(send(HttpMethod.DELETE, "/api/v1/join-requests/" + UUID.randomUUID(), applicant, null).getStatus())
                .isEqualTo(404);

        // Duyệt kèm hồ sơ: người dưới 16 tuổi trả GEN_012 và yêu cầu vẫn PENDING; hồ sơ người lớn thì gắn được.
        UUID third = createdId(join(applicant, a.id()));
        UUID child = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.id() + "/persons", a.head(),
                Map.of("fullName", "Cháu nhỏ", "gender", "MALE", "birthDate", "2020-01-01", "isDeceased", false)));
        MockHttpServletResponse underAge = send(HttpMethod.PATCH, "/api/v1/join-requests/" + third, a.head(),
                Map.of("status", "APPROVED", "personId", child.toString()));
        assertThat(code(underAge)).isEqualTo("GEN_012");
        assertThat(jdbc.queryForObject("select status from member_verification where id = ?", String.class, third))
                .isEqualTo("PENDING");
        UUID adult = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.id() + "/persons", a.head(),
                Map.of("fullName", "Người xin", "gender", "FEMALE", "birthDate", "1990-01-01", "isDeceased", false)));
        assertThat(send(HttpMethod.PATCH, "/api/v1/join-requests/" + third, a.head(),
                Map.of("status", "APPROVED", "personId", adult.toString())).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select user_id from person where id = ?", UUID.class, adult))
                .isEqualTo(body(send(HttpMethod.GET, "/api/v1/users/me", applicant, null)).at("/data/id").asText()
                        .transform(UUID::fromString));
    }

    @Test
    void headAssignsBranchWhenApprovingFamilyCodeRequest() throws Exception {
        Family a = family("Dòng họ F");
        UUID branch = createdId(send(HttpMethod.POST, "/api/v1/families/" + a.id() + "/branches", a.head(),
                Map.of("name", "Chi ba")));
        String applicant = token(newUser("Người xin"));
        UUID requestId = createdId(join(applicant, a.id()));
        assertThat(send(HttpMethod.PATCH, "/api/v1/join-requests/" + requestId, a.head(),
                Map.of("status", "APPROVED", "branchId", UUID.randomUUID().toString())).getStatus()).isEqualTo(404);
        MockHttpServletResponse approved = send(HttpMethod.PATCH, "/api/v1/join-requests/" + requestId, a.head(),
                Map.of("status", "APPROVED", "branchId", branch.toString()));
        assertThat(approved.getStatus()).isEqualTo(200);
        assertThat(body(approved).at("/data/branchId").asText()).isEqualTo(branch.toString());
        JsonNode membership = body(send(HttpMethod.GET, "/api/v1/users/me/membership", applicant, null)).get("data");
        assertThat(membership.get("status").asText()).isEqualTo("APPROVED");
        assertThat(membership.get("branchName").asText()).isEqualTo("Chi ba");
    }
}
