package com.familyconnect.modules.genealogy;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-GEN-04, UC-GEN-05 qua HTTP với PostgreSQL thật; mỗi test tự rollback, dữ liệu giả.
 * Mỗi quy tắc BR-GEN-02 đến 08 có ít nhất một ca; thêm phân quyền BR-GEN-14 và IDOR giữa gia đình.
 * Lỗi nghiệp vụ không ghi DB nên nhiều ca chạy được trong cùng giao dịch của test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelationshipApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "rel." + UUID.randomUUID() + "@example.com";
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

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("data").get("id").asText());
    }

    /** Chủ gia đình mới (BRANCH_ADMIN toàn gia đình): [token, familyId]. */
    private Object[] owner() throws Exception {
        String token = token(newUser());
        UUID familyId = id(call(post("/api/v1/families"), token, Map.of("name", "Dòng họ thử")).andExpect(status().isCreated()));
        return new Object[]{token, familyId};
    }

    private UUID person(String token, UUID familyId, String name, String gender, String birthDate, UUID branchId)
            throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", name, "gender", gender, "birthDate", birthDate));
        if (branchId != null) body.put("branchId", branchId.toString());
        return id(call(post("/api/v1/families/" + familyId + "/persons"), token, body).andExpect(status().isCreated()));
    }

    private ResultActions addParent(String token, UUID child, UUID parent, String role, String kind) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("parentId", parent.toString(), "parentRole", role));
        if (kind != null) body.put("kind", kind);
        return call(post("/api/v1/persons/" + child + "/parents"), token, body);
    }

    private ResultActions marry(String token, UUID a, UUID b) throws Exception {
        return call(post("/api/v1/marriages"), token, Map.of("person1Id", a.toString(), "person2Id", b.toString()));
    }

    private String member(UUID familyId, UUID branchId, String role) throws Exception {
        String email = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "select id, ?, ?, ?, 'APPROVED' from users where email = ?", familyId, branchId, role, email);
        return token(email);
    }

    @Test
    void parentRulesFollowBrGen02To05() throws Exception {
        Object[] a = owner();
        String t = (String) a[0];
        UUID fam = (UUID) a[1];
        UUID grandpa = person(t, fam, "Ông", "MALE", "1930-01-01", null);
        UUID dad = person(t, fam, "Cha", "MALE", "1960-01-01", null);
        UUID otherDad = person(t, fam, "Cha nuôi", "MALE", "1958-01-01", null);
        UUID mom = person(t, fam, "Mẹ", "FEMALE", "1962-01-01", null);
        UUID kid = person(t, fam, "Con", "MALE", "1990-01-01", null);
        UUID baby = person(t, fam, "Cháu", "FEMALE", "2020-01-01", null);

        addParent(t, kid, dad, "FATHER", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.parentId").value(dad.toString()))
                .andExpect(jsonPath("$.data.childId").value(kid.toString()))
                .andExpect(jsonPath("$.data.kind").value("BIOLOGICAL"));
        addParent(t, kid, dad, "FATHER", null).andExpect(jsonPath("$.error.code").value("GEN_008"));
        addParent(t, kid, kid, "FATHER", null).andExpect(jsonPath("$.error.code").value("GEN_008"));
        addParent(t, kid, otherDad, "FATHER", "BIOLOGICAL")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_005"));
        addParent(t, kid, otherDad, "FATHER", "ADOPTED").andExpect(status().isCreated()); // nuôi không tính giới hạn
        addParent(t, kid, mom, "MOTHER", null).andExpect(status().isCreated());
        addParent(t, dad, grandpa, "FATHER", null).andExpect(status().isCreated());
        // Cháu làm cha của ông: tạo vòng lặp; dùng người sinh sớm để không vướng GEN_007 trước.
        addParent(t, grandpa, kid, "FATHER", null).andExpect(jsonPath("$.error.code").value("GEN_006"));
        addParent(t, kid, baby, "MOTHER", "ADOPTED").andExpect(jsonPath("$.error.code").value("GEN_007"));
        addParent(t, kid, mom, "UNCLE", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));

        call(delete("/api/v1/persons/" + kid + "/parents/" + otherDad), t, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        call(delete("/api/v1/persons/" + kid + "/parents/" + otherDad), t, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND_001"));
        call(delete("/api/v1/persons/" + dad), t, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_004"));
    }

    @Test
    void marriageRulesFollowBrGen07And08() throws Exception {
        Object[] a = owner();
        String t = (String) a[0];
        UUID fam = (UUID) a[1];
        UUID dad = person(t, fam, "Cha", "MALE", "1960-01-01", null);
        UUID son = person(t, fam, "Con trai", "MALE", "1990-01-01", null);
        UUID daughter = person(t, fam, "Con gái", "FEMALE", "1992-01-01", null);
        UUID wife1 = person(t, fam, "Vợ một", "FEMALE", "1991-01-01", null);
        UUID wife2 = person(t, fam, "Vợ hai", "FEMALE", "1993-01-01", null);
        addParent(t, son, dad, "FATHER", null).andExpect(status().isCreated());
        addParent(t, daughter, dad, "FATHER", null).andExpect(status().isCreated());

        UUID marriage = id(marry(t, son, wife1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MARRIED")));
        marry(t, son, wife2).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GEN_009"));
        marry(t, dad, daughter).andExpect(jsonPath("$.error.code").value("GEN_010")); // trực hệ
        marry(t, son, daughter).andExpect(jsonPath("$.error.code").value("GEN_009")); // con trai đang có vợ
        UUID cousinLess = person(t, fam, "Em trai", "MALE", "1995-01-01", null);
        addParent(t, cousinLess, dad, "FATHER", null).andExpect(status().isCreated());
        marry(t, cousinLess, daughter).andExpect(jsonPath("$.error.code").value("GEN_010")); // anh chị em ruột
        marry(t, son, son).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));

        call(put("/api/v1/marriages/" + marriage), t, Map.of("status", "DIVORCED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));
        call(put("/api/v1/marriages/" + marriage), t, Map.of("status", "DIVORCED", "endDate", "2020-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DIVORCED"))
                .andExpect(jsonPath("$.data.endDate").value("2020-05-01"));
        UUID remarriage = id(marry(t, son, wife2).andExpect(status().isCreated())); // tái hôn sau ly hôn

        // BR-GEN-08: đánh dấu đã mất thì hôn nhân MARRIED chuyển WIDOWED, và không thêm hôn nhân mới.
        call(put("/api/v1/persons/" + wife2), t, Map.of("fullName", "Vợ hai", "gender", "FEMALE",
                "birthDate", "1993-01-01", "isDeceased", true, "deathDate", "2024-01-01")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from marriage where id = ?", String.class, remarriage))
                .isEqualTo("WIDOWED");
        marry(t, cousinLess, wife2).andExpect(jsonPath("$.error.code").value("GEN_011"));

        call(delete("/api/v1/marriages/" + marriage), t, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        call(put("/api/v1/marriages/" + marriage), t, Map.of("status", "MARRIED"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsInScopeChangeRelationsAndOtherFamiliesAreBlocked() throws Exception {
        Object[] a = owner();
        String t = (String) a[0];
        UUID fam = (UUID) a[1];
        UUID branch = id(call(post("/api/v1/families/" + fam + "/branches"), t, Map.of("name", "Chi một")));
        UUID dad = person(t, fam, "Cha", "MALE", "1960-01-01", branch);
        UUID kid = person(t, fam, "Con", "MALE", "1990-01-01", branch);
        UUID outside = person(t, fam, "Ngoài chi", "MALE", "1992-01-01", null);
        UUID outsideWife = person(t, fam, "Vợ ngoài chi", "FEMALE", "1993-01-01", null);

        String plainMember = member(fam, null, "MEMBER");
        addParent(plainMember, kid, dad, "FATHER", null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        marry(plainMember, outside, outsideWife).andExpect(status().isForbidden());

        String branchAdmin = member(fam, branch, "BRANCH_ADMIN");
        addParent(branchAdmin, kid, dad, "FATHER", null).andExpect(status().isCreated());
        addParent(branchAdmin, outside, dad, "FATHER", null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        marry(branchAdmin, outside, outsideWife).andExpect(status().isForbidden()); // không ai thuộc chi mình
        marry(branchAdmin, kid, outsideWife).andExpect(status().isCreated()); // một bên thuộc chi mình

        String unverified = token(newUser());
        addParent(unverified, kid, dad, "MOTHER", "STEP")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_002"));

        Object[] b = owner();
        String otherOwner = (String) b[0];
        UUID strangerParent = person(otherOwner, (UUID) b[1], "Người nhà B", "MALE", "1950-01-01", null);
        addParent(otherOwner, kid, strangerParent, "FATHER", "ADOPTED")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        addParent(t, kid, strangerParent, "MOTHER", "STEP")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(delete("/api/v1/persons/" + kid + "/parents/" + dad), otherOwner, null)
                .andExpect(status().isForbidden());
    }
}
