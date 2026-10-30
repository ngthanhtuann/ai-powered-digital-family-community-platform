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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /families/{familyId}/tree (UC-GEN-06) với PostgreSQL thật; mỗi test tự rollback, dữ liệu giả.
 * Bao phủ: định dạng nodes/edges cho React Flow, thế hệ theo genealogy.md 6.8, lazy-load, BR-GEN-10, IDOR,
 * và tiêu chí Jira SCRUM-45 "cây 500 người dưới 500ms".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FamilyTreeApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "tree." + UUID.randomUUID() + "@example.com";
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

    private JsonNode tree(String token, UUID familyId, String query) throws Exception {
        String body = call(get("/api/v1/families/" + familyId + "/tree" + query), token, null)
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(body).get("data");
    }

    private static JsonNode node(JsonNode data, UUID id) {
        for (JsonNode n : data.get("nodes")) if (n.get("id").asText().equals(id.toString())) return n;
        return null;
    }

    private String member(UUID familyId) throws Exception {
        String email = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, role, status) "
                + "select id, ?, 'MEMBER', 'APPROVED' from users where email = ?", familyId, email);
        return token(email);
    }

    @Test
    void treeHasDescendantsSpousesGenerationsLazyLoadAndChildPrivacy() throws Exception {
        Object[] a = owner();
        String t = (String) a[0];
        UUID fam = (UUID) a[1];
        UUID grandpa = person(t, fam, "Ông Tổ", "MALE", "1930-01-01");
        UUID dad = person(t, fam, "Cha", "MALE", "1960-01-01");
        UUID mom = person(t, fam, "Mẹ (dâu)", "FEMALE", "1962-01-01");
        UUID kid = person(t, fam, "Bé Na", "FEMALE", LocalDate.now().minusYears(8).toString());
        parent(t, dad, grandpa, "FATHER");
        parent(t, kid, dad, "FATHER");
        parent(t, kid, mom, "MOTHER");
        call(post("/api/v1/marriages"), t, Map.of("person1Id", dad.toString(), "person2Id", mom.toString()))
                .andExpect(status().isCreated());

        JsonNode two = tree(t, fam, "?rootId=" + grandpa + "&depth=2");
        assertThat(two.get("rootId").asText()).isEqualTo(grandpa.toString());
        assertThat(two.get("nodes")).hasSize(3); // ông, cha và vợ của cha; con ở đời thứ 3 chưa tải
        assertThat(node(two, kid)).isNull();
        assertThat(node(two, dad).get("hasMoreChildren").asBoolean()).isTrue();
        assertThat(node(two, grandpa).get("generation").asInt()).isEqualTo(1);
        assertThat(node(two, mom).get("generation").asInt()).isEqualTo(2); // con dâu lấy đời của chồng
        List<String> edgeTypes = new ArrayList<>();
        two.get("edges").forEach(e -> edgeTypes.add(e.get("type").asText() + ":" + e.get("source").asText()));
        assertThat(edgeTypes).contains("PARENT_CHILD:" + grandpa, "MARRIAGE:" + dad);

        // Mặc định: người tổ (không có cha mẹ, sinh sớm nhất); lazy-load nhánh của cha.
        assertThat(tree(t, fam, "").get("rootId").asText()).isEqualTo(grandpa.toString());
        JsonNode branch = tree(t, fam, "?rootId=" + dad + "&depth=2");
        assertThat(node(branch, kid)).isNotNull();
        assertThat(node(branch, kid).get("gender").asText()).isEqualTo("FEMALE"); // trưởng chi thấy đủ

        JsonNode asMember = tree(member(fam), fam, "?rootId=" + dad + "&depth=2");
        JsonNode kidForMember = node(asMember, kid);
        assertThat(kidForMember.get("fullName").asText()).isEqualTo("Bé Na");
        assertThat(kidForMember.get("birthYear").asInt()).isEqualTo(LocalDate.now().minusYears(8).getYear());
        assertThat(kidForMember.get("gender").isNull()).isTrue();
        assertThat(kidForMember.get("generation").isNull()).isTrue();
        assertThat(asMember.toString()).doesNotContain("birthDate");
    }

    @Test
    void rejectsBadDepthOtherFamilyAndUnverifiedUsers() throws Exception {
        Object[] a = owner();
        String t = (String) a[0];
        UUID fam = (UUID) a[1];
        assertThat(tree(t, fam, "").get("nodes")).isEmpty(); // UC-GEN-06 2a: gia đình chưa có thành viên
        UUID someone = person(t, fam, "Người A", "MALE", "1980-01-01");

        call(get("/api/v1/families/" + fam + "/tree?depth=0"), t, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));
        call(get("/api/v1/families/" + fam + "/tree?depth=11"), t, null).andExpect(status().isBadRequest());

        Object[] b = owner();
        String otherOwner = (String) b[0];
        UUID otherPerson = person(otherOwner, (UUID) b[1], "Người B", "MALE", "1980-01-01");
        call(get("/api/v1/families/" + fam + "/tree"), otherOwner, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(get("/api/v1/families/" + fam + "/tree?rootId=" + otherPerson), t, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND_001"));
        call(get("/api/v1/families/" + fam + "/tree?rootId=" + someone), token(newUser()), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_002"));
    }

    @Test
    void fiveHundredPersonTreeLoadsUnder500ms() throws Exception {
        Object[] a = owner();
        String t = (String) a[0];
        UUID fam = (UUID) a[1];
        UUID actor = jdbc.queryForObject("select created_by from family where id = ?", UUID.class, fam);
        // Cây 500 người, mỗi người tối đa 3 con (khoảng 6 đời); chèn thẳng để dựng dữ liệu nhanh.
        List<UUID> people = new ArrayList<>();
        Map<UUID, UUID> parentOf = new HashMap<>();
        for (int i = 0; i < 500; i++) {
            UUID id = UUID.randomUUID();
            people.add(id);
            if (i > 0) parentOf.put(id, people.get((i - 1) / 3));
        }
        jdbc.batchUpdate("insert into person(id, family_id, full_name, gender, birth_date, created_by) values (?, ?, ?, 'MALE', ?, ?)",
                people.stream().map(id -> new Object[]{id, fam, "Người " + people.indexOf(id),
                        java.sql.Date.valueOf(LocalDate.of(1900, 1, 1).plusYears(people.indexOf(id) / 30)), actor}).toList());
        jdbc.batchUpdate("insert into parent_child(parent_id, child_id, parent_role, created_by) values (?, ?, 'FATHER', ?)",
                parentOf.entrySet().stream().map(e -> new Object[]{e.getValue(), e.getKey(), actor}).toList());

        String url = "/families/" + fam + "/tree?rootId=" + people.get(0) + "&depth=10";
        tree(t, fam, url.substring(url.indexOf("?"))); // làm nóng
        long best = Long.MAX_VALUE;
        JsonNode data = null;
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            data = tree(t, fam, url.substring(url.indexOf("?")));
            best = Math.min(best, (System.nanoTime() - start) / 1_000_000);
        }
        assertThat(data.get("nodes")).hasSize(500);
        assertThat(data.get("edges")).hasSize(499);
        assertThat(best).as("Thời gian tải cây 500 người (ms)").isLessThan(500);
    }
}
