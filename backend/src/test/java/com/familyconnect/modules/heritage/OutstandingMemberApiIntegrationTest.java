package com.familyconnect.modules.heritage;

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
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutstandingMemberApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "outstanding." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử",
                                "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).at("/data/accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    @Test
    void outstandingMemberCrudHonorsFamilyReadAndAdminWritePermissions() throws Exception {
        String ownerEmail = newUser();
        String ownerToken = token(ownerEmail);
        UUID ownerId = userId(ownerEmail);
        UUID familyId = UUID.fromString(data(call(post("/api/v1/families"), ownerToken, Map.of("name", "Gia đình"))
                .andExpect(status().isCreated())).get("id").asText());
        UUID branchA = jdbc.queryForObject("""
                INSERT INTO branch(family_id,name,created_by) VALUES (?, 'Chi A', ?) RETURNING id
                """, UUID.class, familyId, ownerId);
        UUID branchB = jdbc.queryForObject("""
                INSERT INTO branch(family_id,name,created_by) VALUES (?, 'Chi B', ?) RETURNING id
                """, UUID.class, familyId, ownerId);
        UUID personId = jdbc.queryForObject("""
                INSERT INTO person(family_id,branch_id,full_name,gender,birth_date,created_by)
                VALUES (?,?,?,'MALE','1980-01-01',?) RETURNING id
                """, UUID.class, familyId, branchA, "Người tiêu biểu", ownerId);
        UUID otherPersonId = jdbc.queryForObject("""
                INSERT INTO person(family_id,branch_id,full_name,gender,birth_date,created_by)
                VALUES (?,?,?,'FEMALE','1985-01-01',?) RETURNING id
                """, UUID.class, familyId, branchB, "Người chi khác", ownerId);

        JsonNode created = data(call(post("/api/v1/heritage/outstanding-members"), ownerToken,
                        Map.of("personId", personId, "achievement", "Huân chương lao động", "displayOrder", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)));
        UUID outstandingId = UUID.fromString(created.get("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Người tiêu biểu", created.get("fullName").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2, created.get("displayOrder").asInt());

        call(post("/api/v1/heritage/outstanding-members"), ownerToken,
                        Map.of("personId", personId, "achievement", "Trùng"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HER_001"));

        String branchAdminEmail = newUser();
        UUID branchAdminId = userId(branchAdminEmail);
        jdbc.update("""
                INSERT INTO member_verification(user_id,family_id,branch_id,role,status)
                VALUES (?,?,?,'BRANCH_ADMIN','APPROVED')
                """, branchAdminId, familyId, branchA);
        String branchAdminToken = token(branchAdminEmail);
        call(get("/api/v1/heritage/outstanding-members"), branchAdminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fullName").value("Người tiêu biểu"));
        call(post("/api/v1/heritage/outstanding-members"), branchAdminToken,
                        Map.of("personId", otherPersonId, "achievement", "Ngoài phạm vi chi"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        UUID otherOutstandingId = UUID.fromString(data(call(post("/api/v1/heritage/outstanding-members"), ownerToken,
                        Map.of("personId", otherPersonId, "achievement", "Thành tích chi B"))
                .andExpect(status().isCreated())).get("id").asText());

        String memberEmail = newUser();
        UUID memberId = userId(memberEmail);
        jdbc.update("""
                INSERT INTO member_verification(user_id,family_id,role,status)
                VALUES (?,?,'MEMBER','APPROVED')
                """, memberId, familyId);
        String memberToken = token(memberEmail);
        call(get("/api/v1/heritage/outstanding-members"), memberToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fullName").value("Người chi khác"))
                .andExpect(jsonPath("$.meta.total").value(2));
        call(post("/api/v1/heritage/outstanding-members"), memberToken,
                        Map.of("personId", personId, "achievement", "Không được thêm"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        call(patch("/api/v1/heritage/outstanding-members/" + outstandingId), ownerToken,
                        Map.of("achievement", "Giải thưởng quốc gia", "displayOrder", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.achievement").value("Giải thưởng quốc gia"))
                .andExpect(jsonPath("$.data.displayOrder").value(1));
        call(patch("/api/v1/heritage/outstanding-members/" + outstandingId), ownerToken, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));

        call(delete("/api/v1/heritage/outstanding-members/" + outstandingId), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        call(get("/api/v1/heritage/outstanding-members"), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));

        // Người bị xóa khỏi gia phả: trưởng chi không còn chi để đối chiếu (PERM_001), trưởng họ vẫn dọn được mục.
        jdbc.update("UPDATE person SET deleted_at = now() WHERE id = ?", otherPersonId);
        call(get("/api/v1/heritage/outstanding-members"), ownerToken, null)
                .andExpect(jsonPath("$.data[0].fullName").value("Thành viên không còn trong gia phả"));
        call(delete("/api/v1/heritage/outstanding-members/" + otherOutstandingId), branchAdminToken, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(delete("/api/v1/heritage/outstanding-members/" + otherOutstandingId), ownerToken, null)
                .andExpect(status().isOk());
    }
}
