package com.familyconnect.modules.genealogy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Gia phả (UC-GEN-01..03) qua HTTP với PostgreSQL thật; mỗi test tự rollback, dữ liệu giả.
 * Bao phủ nhóm test bắt buộc: phân quyền, IDOR giữa gia đình, quyền riêng tư dưới 16 tuổi, quy ước API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GenealogyApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    // ---- tiện ích ----

    private String newUser() throws Exception {
        String email = "gen." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử", "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private void member(String email, UUID familyId, UUID branchId, String role) {
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) values (?, ?, ?, ?, 'APPROVED')",
                userId(email), familyId, branchId, role);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(data(result).get("id").asText());
    }

    private static Map<String, Object> person(String name, LocalDate birthDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", name);
        body.put("gender", "MALE");
        body.put("birthDate", birthDate == null ? null : birthDate.toString());
        body.put("currentProvince", "Hà Nội");
        body.put("currentAddress", "12 Phố Huế");
        return body;
    }

    /** Chủ gia đình mới (tạo qua API), trả về [token, familyId]. */
    private Object[] owner() throws Exception {
        String email = newUser();
        String token = token(email);
        UUID familyId = id(call(post("/api/v1/families"), token, Map.of("name", "Dòng họ thử"))
                .andExpect(status().isCreated()));
        return new Object[]{token, familyId};
    }

    // ---- UC-GEN-01 ----

    @Test
    void creatorBecomesWholeFamilyAdminAndCannotCreateSecondFamily() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];

        call(get("/api/v1/users/me/membership"), token, null)
                .andExpect(jsonPath("$.data.role").value("BRANCH_ADMIN"))
                .andExpect(jsonPath("$.data.familyName").value("Dòng họ thử"));
        call(get("/api/v1/families"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(o[1].toString()));
        call(post("/api/v1/families"), token, Map.of("name", "Gia đình thứ hai"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GEN_013"));
        call(post("/api/v1/families"), token(newUser()), Map.of("name", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));
    }

    @Test
    void familyIsIsolatedAndOnlySystemAdminDeletes() throws Exception {
        Object[] a = owner();
        Object[] b = owner();
        UUID familyA = (UUID) a[1];
        String unverified = token(newUser());

        call(get("/api/v1/families/" + familyA), null, null).andExpect(status().isUnauthorized());
        call(get("/api/v1/families/" + familyA), unverified, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_002"));
        call(get("/api/v1/families/" + familyA), (String) b[0], null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(put("/api/v1/families/" + familyA), (String) b[0], Map.of("name", "Chiếm quyền"))
                .andExpect(status().isForbidden());
        call(put("/api/v1/families/" + familyA), (String) a[0], Map.of("name", "Họ Nguyễn Văn"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Họ Nguyễn Văn"));
        call(delete("/api/v1/families/" + familyA), (String) a[0], null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));

        String adminEmail = newUser();
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'", userId(adminEmail));
        String admin = token(adminEmail);
        call(post("/api/v1/families/" + familyA + "/persons"), admin, person("Cụ tổ", LocalDate.of(1930, 1, 1)))
                .andExpect(status().isCreated());
        call(delete("/api/v1/families/" + familyA), admin, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_001"));
        call(delete("/api/v1/families/" + b[1]), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        call(get("/api/v1/families/" + b[1]), admin, null).andExpect(status().isNotFound());
    }

    // ---- UC-GEN-02 ----

    @Test
    void branchTreeRejectsCyclesForeignParentsAndNonEmptyDelete() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        UUID familyId = (UUID) o[1];
        UUID root = id(call(post("/api/v1/families/" + familyId + "/branches"), token, Map.of("name", "Chi trưởng"))
                .andExpect(status().isCreated()));
        UUID child = id(call(post("/api/v1/families/" + familyId + "/branches"), token,
                Map.of("name", "Chi hai", "parentBranchId", root.toString())).andExpect(status().isCreated()));

        call(put("/api/v1/branches/" + root), token, Map.of("name", "Chi trưởng", "parentBranchId", child.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_003"));
        Object[] other = owner();
        UUID foreign = id(call(post("/api/v1/families/" + other[1] + "/branches"), (String) other[0], Map.of("name", "Chi lạ")));
        call(post("/api/v1/families/" + familyId + "/branches"), token, Map.of("name", "Sai", "parentBranchId", foreign.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("parentBranchId"));
        call(get("/api/v1/families/" + familyId + "/branches"), (String) other[0], null)
                .andExpect(status().isForbidden());

        call(delete("/api/v1/branches/" + root), token, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_002"));
        call(get("/api/v1/families/" + familyId + "/branches?sort=name,asc"), token, null)
                .andExpect(jsonPath("$.meta.total").value(2)).andExpect(jsonPath("$.data[0].name").value("Chi hai"));
        call(delete("/api/v1/branches/" + child), token, null).andExpect(status().isOk());
    }

    @Test
    void branchScopedAdminManagesOnlyItsSubtree() throws Exception {
        Object[] o = owner();
        String ownerToken = (String) o[0];
        UUID familyId = (UUID) o[1];
        UUID root = id(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken, Map.of("name", "Chi trưởng")));
        UUID child = id(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken,
                Map.of("name", "Chi hai", "parentBranchId", root.toString())));
        UUID sibling = id(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken, Map.of("name", "Chi thứ")));
        String adminEmail = newUser();
        member(adminEmail, familyId, root, "BRANCH_ADMIN");
        String scoped = token(adminEmail);

        Map<String, Object> inChild = person("Người chi hai", LocalDate.of(1980, 1, 1));
        inChild.put("branchId", child.toString());
        call(post("/api/v1/families/" + familyId + "/persons"), scoped, inChild).andExpect(status().isCreated());
        Map<String, Object> inSibling = person("Người chi thứ", LocalDate.of(1980, 1, 1));
        inSibling.put("branchId", sibling.toString());
        call(post("/api/v1/families/" + familyId + "/persons"), scoped, inSibling)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(post("/api/v1/families/" + familyId + "/persons"), scoped, person("Không chi", LocalDate.of(1980, 1, 1)))
                .andExpect(status().isForbidden());
        call(post("/api/v1/families/" + familyId + "/branches"), scoped, Map.of("name", "Chi gốc mới"))
                .andExpect(status().isForbidden());
        call(post("/api/v1/families/" + familyId + "/branches"), scoped,
                Map.of("name", "Chi cháu", "parentBranchId", child.toString())).andExpect(status().isCreated());
        call(put("/api/v1/families/" + familyId), scoped, Map.of("name", "Đổi tên")).andExpect(status().isForbidden());
    }

    // ---- UC-GEN-03 ----

    @Test
    void personValidationFollowsBusinessRules() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        String url = "/api/v1/families/" + o[1] + "/persons";

        call(post(url), token, person("Thiếu ngày sinh", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("birthDate"));
        Map<String, Object> deathBeforeBirth = person("Mất trước sinh", LocalDate.of(1950, 5, 1));
        deathBeforeBirth.put("isDeceased", true);
        deathBeforeBirth.put("deathDate", "1949-01-01");
        call(post(url), token, deathBeforeBirth)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("deathDate"));
        Map<String, Object> ancestor = new HashMap<>(Map.of("fullName", "Cụ tổ", "gender", "MALE",
                "isDeceased", true, "birthYear", 1850, "burialPlace", "Nghĩa trang họ"));
        call(post(url), token, ancestor).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.birthYear").value(1850))
                .andExpect(jsonPath("$.data.isDeceased").value(true));
        Map<String, Object> badGender = person("Sai giới tính", LocalDate.of(1990, 1, 1));
        badGender.put("gender", "UNKNOWN");
        call(post(url), token, badGender).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));
        call(get(url + "?size=101"), token, null).andExpect(status().isBadRequest());
        call(get(url + "?sort=password,asc"), token, null).andExpect(status().isBadRequest());
    }

    @Test
    void privacyHidesSensitiveFieldsForMembersAndChildren() throws Exception {
        Object[] o = owner();
        String ownerToken = (String) o[0];
        UUID familyId = (UUID) o[1];
        String url = "/api/v1/families/" + familyId + "/persons";
        UUID adult = id(call(post(url), ownerToken, person("Người lớn", LocalDate.of(1980, 3, 4))));
        UUID child = id(call(post(url), ownerToken, person("Em bé", LocalDate.now().minusYears(8))));
        String memberEmail = newUser();
        member(memberEmail, familyId, null, "MEMBER");
        String memberToken = token(memberEmail);

        call(get("/api/v1/persons/" + adult), memberToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthYear").value(1980))
                .andExpect(jsonPath("$.data.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.data.currentProvince").value("Hà Nội"))
                .andExpect(jsonPath("$.data.currentAddress").value(nullValue()))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
        call(get("/api/v1/persons/" + child), memberToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Em bé"))
                .andExpect(jsonPath("$.data.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.data.currentProvince").value(nullValue()));
        call(get("/api/v1/persons/" + adult), ownerToken, null)
                .andExpect(jsonPath("$.data.birthDate").value("1980-03-04"))
                .andExpect(jsonPath("$.data.currentAddress").value("12 Phố Huế"));

        // Thành viên được gắn với hồ sơ thì thấy ngày sinh đầy đủ của chính mình.
        Map<String, Object> self = person("Chính tôi", LocalDate.of(1995, 7, 7));
        self.put("userId", userId(memberEmail).toString());
        UUID selfId = id(call(post(url), ownerToken, self).andExpect(status().isCreated()));
        call(get("/api/v1/persons/" + selfId), memberToken, null)
                .andExpect(jsonPath("$.data.birthDate").value("1995-07-07"));
        call(get(url + "?q=em"), memberToken, null).andExpect(jsonPath("$.meta.total").value(1));
        call(post(url), memberToken, person("Không được thêm", LocalDate.of(1990, 1, 1)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    @Test
    void accountLinkingRejectsChildrenAndOtherFamilies() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        String url = "/api/v1/families/" + o[1] + "/persons";
        String outsider = newUser();

        Map<String, Object> child = person("Trẻ em", LocalDate.now().minusYears(10));
        child.put("userId", userId(outsider).toString());
        call(post(url), token, child).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_012"));
        Map<String, Object> adult = person("Người lớn", LocalDate.of(1985, 1, 1));
        adult.put("userId", userId(outsider).toString());
        call(post(url), token, adult).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_014"));
    }

    @Test
    void personIsIsolatedBetweenFamilies() throws Exception {
        Object[] a = owner();
        Object[] b = owner();
        UUID personA = id(call(post("/api/v1/families/" + a[1] + "/persons"), (String) a[0], person("Người họ A", LocalDate.of(1970, 1, 1))));

        call(get("/api/v1/persons/" + personA), (String) b[0], null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(get("/api/v1/families/" + a[1] + "/persons"), (String) b[0], null).andExpect(status().isForbidden());
        call(put("/api/v1/persons/" + personA), (String) b[0], person("Sửa trộm", LocalDate.of(1970, 1, 1)))
                .andExpect(status().isForbidden());
        call(delete("/api/v1/persons/" + personA), (String) b[0], null).andExpect(status().isForbidden());
        call(get("/api/v1/persons/" + UUID.randomUUID()), (String) a[0], null).andExpect(status().isNotFound());
    }

    @Test
    void deathWidowsMarriageAndRelationsBlockDelete() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        String url = "/api/v1/families/" + o[1] + "/persons";
        UUID husband = id(call(post(url), token, person("Chồng", LocalDate.of(1950, 1, 1))));
        UUID wife = id(call(post(url), token, person("Vợ", LocalDate.of(1952, 1, 1))));
        jdbc.update("insert into marriage(person1_id, person2_id, status, created_by) values (?, ?, 'MARRIED', ?)",
                husband, wife, UUID.randomUUID());

        call(delete("/api/v1/persons/" + husband), token, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_004"));
        Map<String, Object> dead = person("Chồng", LocalDate.of(1950, 1, 1));
        dead.put("isDeceased", true);
        dead.put("deathDate", "2020-02-02");
        call(put("/api/v1/persons/" + husband), token, dead).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deathDate").value("2020-02-02"));
        String status = jdbc.queryForObject("select status || '|' || end_date from marriage where person1_id = ?", String.class, husband);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("WIDOWED|2020-02-02");

        UUID single = id(call(post(url), token, person("Độc thân", LocalDate.of(1990, 1, 1))));
        call(delete("/api/v1/persons/" + single), token, null).andExpect(status().isOk());
        call(get("/api/v1/persons/" + single), token, null).andExpect(status().isNotFound());
    }
}
