package com.familyconnect.modules.genealogy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        // SCRUM-102: gõ không dấu, khác hoa thường vẫn khớp; "%" là ký tự thường, không phải ký tự đại diện.
        call(get(url + "?q=EM BE"), memberToken, null).andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Em bé"));
        call(get(url + "?q=chinh toi"), memberToken, null).andExpect(jsonPath("$.meta.total").value(1));
        call(get(url + "?q=%25"), memberToken, null).andExpect(jsonPath("$.meta.total").value(0));
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

    // ---- Ảnh đại diện và giữ liên kết tài khoản (genealogy.yaml PersonInput, UC-GEN-03 luồng 3g, 3h) ----

    private UUID uploadAvatar(String token, String ownerType, UUID ownerId) throws Exception {
        byte[] png = new byte[64];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, png, 0, signature.length);
        String body = mvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", png))
                        .param("ownerType", ownerType).param("ownerId", ownerId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("data").get("id").asText());
    }

    @Test
    void avatarMustBeUploadedForThatSamePerson() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        UUID familyId = (UUID) o[1];
        String url = "/api/v1/families/" + familyId + "/persons";
        UUID first = id(call(post(url), token, person("Người một", LocalDate.of(1980, 1, 1))));
        UUID second = id(call(post(url), token, person("Người hai", LocalDate.of(1982, 1, 1))));
        UUID firstAvatar = uploadAvatar(token, "PERSON", first);

        Map<String, Object> withAvatar = person("Người ba", LocalDate.of(1984, 1, 1));
        withAvatar.put("avatarMediaId", firstAvatar.toString());
        call(post(url), token, withAvatar).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("avatarMediaId"));

        Map<String, Object> borrow = person("Người hai", LocalDate.of(1982, 1, 1));
        borrow.put("avatarMediaId", firstAvatar.toString());
        call(put("/api/v1/persons/" + second), token, borrow).andExpect(status().isBadRequest());
        borrow.put("avatarMediaId", uploadAvatar(token, "HERITAGE", familyId).toString());
        call(put("/api/v1/persons/" + second), token, borrow).andExpect(status().isBadRequest());

        Map<String, Object> own = person("Người một", LocalDate.of(1980, 1, 1));
        own.put("avatarMediaId", firstAvatar.toString());
        call(put("/api/v1/persons/" + first), token, own).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarMediaId").value(firstAvatar.toString()));
    }

    @Test
    void updateWithoutUserIdKeepsLinkedAccount() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        UUID familyId = (UUID) o[1];
        String memberEmail = newUser();
        member(memberEmail, familyId, null, "MEMBER");
        Map<String, Object> linked = person("Có tài khoản", LocalDate.of(1990, 2, 2));
        linked.put("userId", userId(memberEmail).toString());
        UUID personId = id(call(post("/api/v1/families/" + familyId + "/persons"), token, linked));

        // BR-GEN-20: hồ sơ đã gắn tài khoản thì Trưởng họ không sửa được, chỉ chính chủ sửa.
        call(put("/api/v1/persons/" + personId), token, person("Trưởng họ đổi", LocalDate.of(1990, 2, 2)))
                .andExpect(status().isForbidden());
        call(put("/api/v1/persons/" + personId), token(memberEmail), person("Đổi tên", LocalDate.of(1990, 2, 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Đổi tên"))
                .andExpect(jsonPath("$.data.canEdit").value(true));
        // Chính chủ không tự chuyển hồ sơ sang tài khoản khác.
        String otherEmail = newUser();
        member(otherEmail, familyId, null, "MEMBER");
        Map<String, Object> relink = person("Đổi tên", LocalDate.of(1990, 2, 2));
        relink.put("userId", userId(otherEmail).toString());
        call(put("/api/v1/persons/" + personId), token(memberEmail), relink).andExpect(status().isForbidden());
        // SCRUM-122: quản trị hệ thống cũng không sửa thông tin cá nhân của hồ sơ đã gắn tài khoản.
        String adminEmail = newUser();
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'", userId(adminEmail));
        call(put("/api/v1/persons/" + personId), token(adminEmail), person("Admin đổi", LocalDate.of(1990, 2, 2)))
                .andExpect(status().isForbidden());
        // SCRUM-119: chính chủ tự tải ảnh đại diện và gán; Trưởng họ không tải ảnh vào hồ sơ đã gắn tài khoản.
        UUID photo = uploadAvatar(token(memberEmail), "PERSON", personId);
        Map<String, Object> withPhoto = person("Đổi tên", LocalDate.of(1990, 2, 2));
        withPhoto.put("avatarMediaId", photo.toString());
        call(put("/api/v1/persons/" + personId), token(memberEmail), withPhoto)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarMediaId").value(photo.toString()));
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        mvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png))
                        .param("ownerType", "PERSON").param("ownerId", personId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        // Trang cá nhân của tôi; Trưởng họ thấy canEdit=false với hồ sơ đã gắn tài khoản.
        call(get("/api/v1/persons/me"), token(memberEmail), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(personId.toString()));
        call(get("/api/v1/persons/" + personId), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canEdit").value(false));

        UUID stillLinked = jdbc.queryForObject("select user_id from person where id = ?", UUID.class, personId);
        org.assertj.core.api.Assertions.assertThat(stillLinked).isEqualTo(userId(memberEmail));
    }

    // ---- BR-GEN-21, BR-AUTH-23, BR-COM-15 (SCRUM-134): người quản lý báo mất, báo nhầm thì khôi phục ----

    private int deathNotices(UUID personId, UUID userId) {
        return jdbc.queryForObject("select count(*) from notification where ref_id = ? and user_id = ? "
                + "and type = 'DEATH_NOTICE' and deleted_at is null", Integer.class, personId, userId);
    }

    @Test
    void headMarksLinkedPersonDeceasedThenRevertsMistake() throws Exception {
        Object[] o = owner();
        String headToken = (String) o[0];
        UUID familyId = (UUID) o[1];
        UUID headId = jdbc.queryForObject("select user_id from member_verification where family_id = ? and role = 'BRANCH_ADMIN'",
                UUID.class, familyId);
        UUID branch = id(call(post("/api/v1/families/" + familyId + "/branches"), headToken, Map.of("name", "Chi một")));
        UUID otherBranch = id(call(post("/api/v1/families/" + familyId + "/branches"), headToken, Map.of("name", "Chi hai")));
        String deadEmail = newUser();
        member(deadEmail, familyId, branch, "MEMBER");
        String kinEmail = newUser();
        member(kinEmail, familyId, branch, "MEMBER");
        String outsiderEmail = newUser();
        member(outsiderEmail, familyId, otherBranch, "MEMBER");
        String persons = "/api/v1/families/" + familyId + "/persons";
        Map<String, Object> linked = person("Ông Báo Mất", LocalDate.of(1950, 3, 3));
        linked.put("userId", userId(deadEmail).toString());
        linked.put("branchId", branch.toString());
        UUID personId = id(call(post(persons), headToken, linked));
        UUID wife = id(call(post(persons), headToken, person("Bà Còn Sống", LocalDate.of(1952, 1, 1))));
        jdbc.update("insert into marriage(person1_id, person2_id, status, created_by) values (?, ?, 'MARRIED', ?)",
                personId, wife, UUID.randomUUID());
        String url = "/api/v1/persons/" + personId + "/death";
        Map<String, Object> death = Map.of("isDeceased", true, "deathDate", "2026-11-20", "deathTime", "14:30",
                "burialPlace", "Nghĩa trang quê nhà");

        // Thành viên thường không báo mất trực tiếp; thiếu ngày mất hoặc giờ sai định dạng: 400.
        call(put(url), token(kinEmail), death).andExpect(status().isForbidden());
        call(put(url), headToken, Map.of("isDeceased", true)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));
        call(put(url), headToken, Map.of("isDeceased", true, "deathDate", "2026-11-20", "deathTime", "25:00"))
                .andExpect(status().isBadRequest());

        // Trưởng họ báo mất hồ sơ đã gắn tài khoản (ngoại lệ BR-GEN-20).
        call(put(url), headToken, death).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDeceased").value(true))
                .andExpect(jsonPath("$.data.deathTime").value("14:30"))
                .andExpect(jsonPath("$.data.canManage").value(true));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status || '|' || lock_reason from users where id = ?", String.class, userId(deadEmail)))
                .isEqualTo("LOCKED|Đã mất");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from marriage where person1_id = ?", String.class, personId)).isEqualTo("WIDOWED");
        org.assertj.core.api.Assertions.assertThat(deathNotices(personId, userId(kinEmail))).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(deathNotices(personId, headId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(deathNotices(personId, userId(outsiderEmail))).isZero();
        org.assertj.core.api.Assertions.assertThat(deathNotices(personId, userId(deadEmail))).isZero();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", deadEmail, "password", PASSWORD))))
                .andExpect(status().is4xxClientError());

        // Báo nhầm: khôi phục còn sống, mở khóa, hôn nhân quay lại MARRIED, thu hồi thông báo.
        call(put(url), headToken, Map.of("isDeceased", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDeceased").value(false));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from users where id = ?", String.class, userId(deadEmail))).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from marriage where person1_id = ?", String.class, personId)).isEqualTo("MARRIED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select death_time is null and death_date is null from person where id = ?", Boolean.class, personId)).isTrue();
        org.assertj.core.api.Assertions.assertThat(deathNotices(personId, userId(kinEmail))).isZero();
        token(deadEmail);
    }

    @Test
    void cannotReportOwnDeathOrUnlockAccountLockedForOtherReasons() throws Exception {
        Object[] o = owner();
        String headToken = (String) o[0];
        UUID familyId = (UUID) o[1];
        UUID headId = jdbc.queryForObject("select user_id from member_verification where family_id = ? and role = 'BRANCH_ADMIN'",
                UUID.class, familyId);
        Map<String, Object> self = person("Trưởng họ", LocalDate.of(1960, 1, 1));
        self.put("userId", headId.toString());
        UUID selfId = id(call(post("/api/v1/families/" + familyId + "/persons"), headToken, self));
        call(put("/api/v1/persons/" + selfId + "/death"), headToken, Map.of("isDeceased", true, "deathDate", "2026-11-20"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));

        String memberEmail = newUser();
        member(memberEmail, familyId, null, "MEMBER");
        Map<String, Object> linked = person("Bị khóa vì lý do khác", LocalDate.of(1980, 1, 1));
        linked.put("userId", userId(memberEmail).toString());
        UUID personId = id(call(post("/api/v1/families/" + familyId + "/persons"), headToken, linked));
        // Khóa qua API quản trị (không qua JDBC) để cache Hibernate trong giao dịch test thấy đúng trạng thái.
        String adminEmail = newUser();
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'", userId(adminEmail));
        call(patch("/api/v1/admin/users/" + userId(memberEmail)), token(adminEmail),
                Map.of("status", "LOCKED", "lockReason", "Vi phạm")).andExpect(status().isOk());
        call(put("/api/v1/persons/" + personId + "/death"), headToken, Map.of("isDeceased", true, "deathDate", "2026-11-20"))
                .andExpect(status().isOk());
        call(put("/api/v1/persons/" + personId + "/death"), headToken, Map.of("isDeceased", false)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status || '|' || lock_reason from users where id = ?", String.class, userId(memberEmail)))
                .isEqualTo("LOCKED|Vi phạm");
    }

    // ---- BR-GEN-22 (SCRUM-138): con tự thuộc chi của cha mẹ ----

    @Test
    void childInheritsParentBranchPreferringFather() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        UUID familyId = (UUID) o[1];
        String persons = "/api/v1/families/" + familyId + "/persons";
        UUID fatherBranch = id(call(post("/api/v1/families/" + familyId + "/branches"), token, Map.of("name", "Chi nội")));
        UUID motherBranch = id(call(post("/api/v1/families/" + familyId + "/branches"), token, Map.of("name", "Chi ngoại")));
        UUID chosen = id(call(post("/api/v1/families/" + familyId + "/branches"), token, Map.of("name", "Chi tự chọn")));
        Map<String, Object> f = person("Cha", LocalDate.of(1960, 1, 1));
        f.put("branchId", fatherBranch.toString());
        UUID father = id(call(post(persons), token, f));
        Map<String, Object> m = person("Mẹ", LocalDate.of(1962, 1, 1));
        m.put("gender", "FEMALE");
        m.put("branchId", motherBranch.toString());
        UUID mother = id(call(post(persons), token, m));
        UUID child = id(call(post(persons), token, person("Con", LocalDate.of(1990, 1, 1))));
        String branchSql = "select branch_id from person where id = ?";

        call(post("/api/v1/persons/" + child + "/parents"), token, Map.of("parentId", mother.toString(), "parentRole", "MOTHER"))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(branchSql, UUID.class, child)).isEqualTo(motherBranch);
        call(post("/api/v1/persons/" + child + "/parents"), token, Map.of("parentId", father.toString(), "parentRole", "FATHER"))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(branchSql, UUID.class, child)).isEqualTo(fatherBranch);

        // Chi do người quản lý chọn thì giữ nguyên.
        Map<String, Object> c2 = person("Con tự chọn chi", LocalDate.of(1992, 1, 1));
        c2.put("branchId", chosen.toString());
        UUID child2 = id(call(post(persons), token, c2));
        call(post("/api/v1/persons/" + child2 + "/parents"), token, Map.of("parentId", father.toString(), "parentRole", "FATHER"))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(branchSql, UUID.class, child2)).isEqualTo(chosen);
    }

    // ---- SCRUM-136: node cây có năm mất và huy hiệu trưởng họ ----

    @Test
    void treeNodeShowsDeathYearAndFamilyHeadBadge() throws Exception {
        Object[] o = owner();
        String token = (String) o[0];
        UUID familyId = (UUID) o[1];
        UUID headId = jdbc.queryForObject("select user_id from member_verification where family_id = ? and role = 'BRANCH_ADMIN'",
                UUID.class, familyId);
        String persons = "/api/v1/families/" + familyId + "/persons";
        Map<String, Object> head = person("Trưởng họ", LocalDate.of(1960, 1, 1));
        head.put("userId", headId.toString());
        UUID headPerson = id(call(post(persons), token, head));
        Map<String, Object> dead = person("Cụ tổ", LocalDate.of(1900, 1, 1));
        dead.put("isDeceased", true);
        dead.put("deathDate", "1980-05-05");
        UUID deadPerson = id(call(post(persons), token, dead));

        call(get("/api/v1/families/" + familyId + "/tree").param("rootId", headPerson.toString()).param("depth", "1"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].leaderRole").value("FAMILY_HEAD"));
        call(get("/api/v1/families/" + familyId + "/tree").param("rootId", deadPerson.toString()).param("depth", "1"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].deathYear").value(1980))
                .andExpect(jsonPath("$.data.nodes[0].leaderRole").doesNotExist());
    }

    // ---- BR-GEN-23, BR-AUTH-25, BR-COM-16 (SCRUM-140): thay tài khoản cho hồ sơ ----

    @Test
    void headReplacesLostAccountTransferringRoleAndContent() throws Exception {
        Object[] o = owner();
        String headToken = (String) o[0];
        UUID familyId = (UUID) o[1];
        UUID branch = id(call(post("/api/v1/families/" + familyId + "/branches"), headToken, Map.of("name", "Chi thay")));
        String oldEmail = newUser();
        member(oldEmail, familyId, branch, "BRANCH_ADMIN");
        String newEmail = newUser();
        member(newEmail, familyId, branch, "MEMBER");
        UUID oldId = userId(oldEmail);
        UUID newId = userId(newEmail);
        Map<String, Object> linked = person("Người mất tài khoản", LocalDate.of(1980, 1, 1));
        linked.put("userId", oldId.toString());
        linked.put("branchId", branch.toString());
        UUID personId = id(call(post("/api/v1/families/" + familyId + "/persons"), headToken, linked));
        UUID postId = jdbc.queryForObject("insert into post(family_id, type, content, created_by) values (?, 'POST', 'Bài cũ', ?) returning id",
                UUID.class, familyId, oldId);
        jdbc.update("insert into reaction(post_id, type, created_by) values (?, 'LIKE', ?)", postId, oldId);
        jdbc.update("insert into reaction(post_id, type, created_by) values (?, 'LOVE', ?)", postId, newId);

        call(get("/api/v1/persons/" + personId + "/account-candidates"), headToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + newId + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.userId == '" + oldId + "')]").doesNotExist());
        String url = "/api/v1/persons/" + personId + "/account-replacements";
        call(post(url), headToken, Map.of("newUserId", newId.toString())).andExpect(status().isBadRequest());
        call(post(url), token(newEmail), Map.of("newUserId", newId.toString(), "reason", "x")).andExpect(status().isForbidden());
        call(post(url), headToken, Map.of("newUserId", newId.toString(), "reason", "Mất điện thoại"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select user_id from person where id = ?", UUID.class, personId))
                .isEqualTo(newId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select status || '|' || lock_reason from users where id = ?",
                String.class, oldId)).isEqualTo("LOCKED|Thay tài khoản: Mất điện thoại");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, oldId))
                .isEqualTo(1); // không xóa tài khoản cũ
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select role || '|' || branch_id from member_verification where user_id = ? and status = 'APPROVED'", String.class, newId))
                .isEqualTo("BRANCH_ADMIN|" + branch);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from member_verification where user_id = ?", String.class, oldId)).isEqualTo("REMOVED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select created_by from post where id = ?", UUID.class, postId))
                .isEqualTo(newId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select string_agg(type, ',') from reaction where post_id = ? and deleted_at is null", String.class, postId))
                .isEqualTo("LOVE"); // trùng thì giữ cảm xúc của tài khoản mới
    }

    // ---- BR-COM-13 (SCRUM-121): bài "con cháu của tôi"; đặt ở đây vì cần dựng cây gia phả có tài khoản ----

    @Test
    void descendantsAudiencePostVisibleOnlyToDescendantsAndTheirSpouses() throws Exception {
        Object[] o = owner();
        String headToken = (String) o[0];
        UUID familyId = (UUID) o[1];
        String grandpa = newUser(), son = newUser(), wife = newUser();
        for (String email : List.of(grandpa, son, wife)) member(email, familyId, null, "MEMBER");
        UUID[] ids = new UUID[3];
        String[] emails = {grandpa, son, wife};
        for (int k = 0; k < 3; k++) {
            Map<String, Object> body = person("Người " + k, LocalDate.of(1950 + k * 20, 1, 1));
            body.put("userId", userId(emails[k]).toString());
            ids[k] = id(call(post("/api/v1/families/" + familyId + "/persons"), headToken, body));
        }
        jdbc.update("insert into parent_child(parent_id, child_id, parent_role, created_by) values (?, ?, 'FATHER', ?)",
                ids[0], ids[1], userId(grandpa));
        jdbc.update("insert into marriage(person1_id, person2_id, status, created_by) values (?, ?, 'MARRIED', ?)",
                ids[1], ids[2], userId(grandpa));

        UUID postId = id(call(post("/api/v1/posts"), token(grandpa),
                Map.of("content", "Chuyện riêng trong nhà", "type", "POST", "audience", "DESCENDANTS"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.audience").value("DESCENDANTS")));

        for (String email : List.of(grandpa, son, wife)) {
            call(get("/api/v1/posts/" + postId), token(email), null).andExpect(status().isOk());
            call(get("/api/v1/posts"), token(email), null)
                    .andExpect(jsonPath("$.data[?(@.id == '" + postId + "')]").exists());
        }
        // Trưởng họ không phải con cháu: không thấy trên bảng tin, mở thẳng bài bị 403.
        call(get("/api/v1/posts"), headToken, null)
                .andExpect(jsonPath("$.data[?(@.id == '" + postId + "')]").doesNotExist());
        call(get("/api/v1/posts/" + postId), headToken, null).andExpect(status().isForbidden());
        // Chưa gắn hồ sơ gia phả thì không chọn được "con cháu của tôi".
        call(post("/api/v1/posts"), headToken,
                Map.of("content", "Thử", "type", "POST", "audience", "DESCENDANTS")).andExpect(status().isBadRequest());
    }

    // ---- BR-COM-14 (SCRUM-123): trả lời bình luận 1 cấp và thông báo ----

    @Test
    void commentRepliesStayOneLevelAndNotifyPostAuthorAndRepliedUser() throws Exception {
        Object[] o = owner();
        UUID familyId = (UUID) o[1];
        String author = newUser(), commenter = newUser();
        member(author, familyId, null, "MEMBER");
        member(commenter, familyId, null, "MEMBER");
        UUID postId = id(call(post("/api/v1/posts"), token(author), Map.of("content", "Bài thử", "type", "POST"))
                .andExpect(status().isCreated()));
        UUID root = id(call(post("/api/v1/posts/" + postId + "/comments"), token(commenter), Map.of("content", "Hay quá"))
                .andExpect(status().isCreated()));
        UUID reply = id(call(post("/api/v1/posts/" + postId + "/comments"), token(author),
                Map.of("content", "Cảm ơn", "parentId", root.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.parentId").value(root.toString())));
        // Trả lời một câu trả lời vẫn gắn vào bình luận gốc.
        call(post("/api/v1/posts/" + postId + "/comments"), token(commenter), Map.of("content", "Dạ", "parentId", reply.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.parentId").value(root.toString()));

        Integer commented = jdbc.queryForObject("select count(*) from notification where user_id = ? and type = 'POST_COMMENTED'",
                Integer.class, userId(author));
        Integer replied = jdbc.queryForObject("select count(*) from notification where user_id = ? and type = 'COMMENT_REPLIED'",
                Integer.class, userId(commenter));
        org.assertj.core.api.Assertions.assertThat(commented).isEqualTo(1); // "Dạ" trả lời tác giả: chỉ COMMENT_REPLIED
        org.assertj.core.api.Assertions.assertThat(replied).isEqualTo(1);
        Integer replyToAuthor = jdbc.queryForObject("select count(*) from notification where user_id = ? and type = 'COMMENT_REPLIED'",
                Integer.class, userId(author));
        org.assertj.core.api.Assertions.assertThat(replyToAuthor).isEqualTo(1);
    }
}
