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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /relationships (UC-GEN-08, SCRUM-51) với PostgreSQL thật; mỗi test tự rollback, dữ liệu giả.
 * Bộ 50 ca thuật toán nằm ở {@link RelationshipCaseCatalogTest}; ở đây kiểm tầng API: định dạng, quyền, ca #51 đến #53.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KinshipApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "kin." + UUID.randomUUID() + "@example.com";
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
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("data").get("id").asText());
    }

    private Object[] owner() throws Exception {
        String token = token(newUser());
        UUID familyId = id(call(post("/api/v1/families"), token, Map.of("name", "Dòng họ thử")).andExpect(status().isCreated()));
        return new Object[]{token, familyId};
    }

    private UUID person(String token, UUID familyId, String name, String gender, String birthDate) throws Exception {
        return id(call(post("/api/v1/families/" + familyId + "/persons"), token,
                Map.of("fullName", name, "gender", gender, "birthDate", birthDate)).andExpect(status().isCreated()));
    }

    private void parent(String token, UUID child, UUID parent, String role) throws Exception {
        call(post("/api/v1/persons/" + child + "/parents"), token, Map.of("parentId", parent.toString(), "parentRole", role))
                .andExpect(status().isCreated());
    }

    private ResultActions ask(String token, UUID a, UUID b) throws Exception {
        return call(get("/api/v1/relationships?personA=" + a + "&personB=" + b), token, null);
    }

    private JsonNode relation(String token, UUID a, UUID b) throws Exception {
        String body = ask(token, a, b).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("data");
    }

    private String member(UUID familyId) throws Exception {
        String email = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, role, status) "
                + "select id, ?, 'MEMBER', 'APPROVED' from users where email = ?", familyId, email);
        return token(email);
    }

    @Test
    void answersUncleWithPathAndOnlyIdAndNameInPath() throws Exception {
        Object[] o = owner();
        String t = (String) o[0];
        UUID fam = (UUID) o[1];
        UUID ong = person(t, fam, "Ông Tổ", "MALE", "1920-01-01");
        UUID bac = person(t, fam, "Bác Hải", "MALE", "1946-06-15");
        UUID cha = person(t, fam, "Cha Cường", "MALE", "1950-06-15");
        UUID con = person(t, fam, "Con An", "MALE", "1975-06-15");
        parent(t, bac, ong, "FATHER");
        parent(t, cha, ong, "FATHER");
        parent(t, con, cha, "FATHER");

        JsonNode d = relation(member(fam), bac, con); // MEMBER đã xác minh cũng được hỏi
        assertThat(d.get("supported").asBoolean()).isTrue();
        assertThat(d.get("relation").asText()).isEqualTo("bác");
        assertThat(d.get("description").asText()).isEqualTo("Bác Hải là bác của Con An");
        assertThat(d.get("commonAncestorId").asText()).isEqualTo(ong.toString());
        assertThat(d.get("stepsFromA").asInt()).isEqualTo(1);
        assertThat(d.get("stepsFromB").asInt()).isEqualTo(2);
        assertThat(d.get("path").findValuesAsText("id")).containsExactly(
                bac.toString(), ong.toString(), cha.toString(), con.toString());
        List<String> fields = List.copyOf(json.convertValue(d.get("path").get(0), Map.class).keySet());
        assertThat(fields).containsExactlyInAnyOrder("id", "fullName");

        assertThat(relation(t, con, bac).get("relation").asText()).isEqualTo("cháu");
        assertThat(relation(t, con, con).get("relation").asText()).isEqualTo("cùng một người");

        UUID stranger = person(t, fam, "Người ngoài", "FEMALE", "1980-01-01");
        JsonNode none = relation(t, stranger, con);
        assertThat(none.get("supported").asBoolean()).isFalse();
        assertThat(none.get("relation").isNull()).isTrue();
        assertThat(none.get("stepsFromA").isNull()).isTrue();
        assertThat(none.get("path")).isEmpty();
    }

    @Test
    void rejectsOtherFamilyUnverifiedAndMissingPerson() throws Exception {
        Object[] o = owner();
        String t = (String) o[0];
        UUID fam = (UUID) o[1];
        UUID a = person(t, fam, "Người A", "MALE", "1980-01-01");
        Object[] other = owner();
        UUID b = person((String) other[0], (UUID) other[1], "Người B", "MALE", "1980-01-01");

        // #51: A thuộc gia đình khác với người gọi, hoặc A và B khác gia đình
        ask((String) other[0], a, a).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        ask(t, a, b).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        // #52: người gọi chưa được xác minh
        ask(token(newUser()), a, a).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_002"));
        // #53: không tìm thấy A hoặc B
        ask(t, a, UUID.randomUUID()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND_001"));
        call(get("/api/v1/relationships?personA=" + a), t, null).andExpect(status().isBadRequest());
    }
}
