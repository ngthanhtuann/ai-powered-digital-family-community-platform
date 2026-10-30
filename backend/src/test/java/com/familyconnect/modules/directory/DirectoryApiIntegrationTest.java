package com.familyconnect.modules.directory;

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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Directory (UC-DIR-01, UC-DIR-02) với PostgreSQL thật; mỗi test tự rollback, dữ liệu giả.
 * Bao phủ: danh bạ không bộ lọc, thế hệ theo genealogy.md mục 6.8, IDOR giữa hai gia đình.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DirectoryApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "dir." + UUID.randomUUID() + "@example.com";
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
        JsonNode body = json.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.get("data").get("id").asText());
    }

    /** Chủ gia đình mới (BRANCH_ADMIN toàn gia đình), trả về [email, token, familyId]. */
    private Object[] owner() throws Exception {
        String email = newUser();
        String token = token(email);
        UUID familyId = id(call(post("/api/v1/families"), token, Map.of("name", "Dòng họ thử"))
                .andExpect(status().isCreated()));
        return new Object[]{email, token, familyId};
    }

    private UUID person(String token, UUID familyId, String name, String gender, String birthDate) throws Exception {
        return id(call(post("/api/v1/families/" + familyId + "/persons"), token, Map.of(
                "fullName", name, "gender", gender, "birthDate", birthDate, "currentProvince", "Hà Nội"))
                .andExpect(status().isCreated()));
    }

    private void parent(UUID parentId, UUID childId, String role, UUID actor) {
        jdbc.update("insert into parent_child(parent_id, child_id, parent_role, created_by) values (?, ?, ?, ?)",
                parentId, childId, role, actor);
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    @Test
    void directoryWorksWithoutFiltersAndUsesSpouseGeneration() throws Exception {
        Object[] a = owner();
        String token = (String) a[1];
        UUID familyId = (UUID) a[2];
        UUID actor = userId((String) a[0]);
        UUID grandpa = person(token, familyId, "Ông Tổ", "MALE", "1950-06-15");
        UUID father = person(token, familyId, "Cha", "MALE", "1975-06-15");
        UUID mother = person(token, familyId, "Mẹ (dâu)", "FEMALE", "1977-06-15");
        UUID kid = person(token, familyId, "Con nhỏ", "MALE", "2015-06-15");
        parent(grandpa, father, "FATHER", actor);
        parent(father, kid, "FATHER", actor);
        parent(mother, kid, "MOTHER", actor);
        jdbc.update("insert into marriage(person1_id, person2_id, status, created_by) values (?, ?, 'MARRIED', ?)",
                father, mother, actor);

        // FR-DIR-01: không bộ lọc trả toàn bộ danh bạ
        call(get("/api/v1/directory/members"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.meta.total").value(4));
        // Con dâu không có cha mẹ trong gia đình nên lấy đời của chồng (đời 2)
        call(get("/api/v1/directory/members?generation=2&sort=fullName,asc"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data[0].fullName").value("Cha"))
                .andExpect(jsonPath("$.data[1].fullName").value("Mẹ (dâu)"))
                .andExpect(jsonPath("$.data[1].generation").value(2));
        call(get("/api/v1/directory/members?generation=1"), token, null)
                .andExpect(jsonPath("$.meta.total").value(1));
        call(get("/api/v1/directory/members?location=hà"), token, null)
                .andExpect(jsonPath("$.meta.total").value(4));

        call(put("/api/v1/directory/members/" + father + "/profession-profile"), token, Map.of("jobTitle", "Kỹ sư"))
                .andExpect(status().isCreated());
        call(put("/api/v1/directory/members/" + father + "/profession-profile"), token, Map.of("jobTitle", "Kỹ sư cầu đường"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").value("Kỹ sư cầu đường"));
        call(get("/api/v1/directory/members?job=kỹ sư"), token, null)
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Kỹ sư cầu đường"));
    }

    @Test
    void otherFamilyCannotReadOrWriteProfiles() throws Exception {
        Object[] a = owner();
        Object[] b = owner();
        String tokenA = (String) a[1];
        String tokenB = (String) b[1];
        UUID familyA = (UUID) a[2];
        UUID personA = person(tokenA, familyA, "Người nhà A", "MALE", "1980-06-15");
        call(put("/api/v1/directory/members/" + personA + "/education-profile"), tokenA, Map.of("school", "Đại học A"))
                .andExpect(status().isCreated());

        call(get("/api/v1/directory/members/" + personA + "/education-profile"), tokenB, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(put("/api/v1/directory/members/" + personA + "/profession-profile"), tokenB, Map.of("jobTitle", "Xâm nhập"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(get("/api/v1/directory/members?familyId=" + familyA), tokenB, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }
}
