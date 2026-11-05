package com.familyconnect.modules.dashboard;

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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "dashboard." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử",
                                "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private UUID createdId(ResultActions result) throws Exception {
        return UUID.fromString(data(result).get("id").asText());
    }

    private UUID person(UUID familyId, UUID branchId, UUID actorId, String name, String gender,
                        LocalDate birthDate, Integer birthYear, String province, boolean deceased) {
        return jdbc.queryForObject("""
                INSERT INTO person(family_id,branch_id,full_name,gender,birth_date,birth_year,
                                   current_province,is_deceased,created_by)
                VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
                """, UUID.class, familyId, branchId, name, gender, birthDate, birthYear, province, deceased, actorId);
    }

    @Test
    void statisticsRespectManagedBranchScopeAndExcludeMinorsFromProfiles() throws Exception {
        String ownerEmail = newUser();
        String ownerToken = token(ownerEmail);
        UUID ownerId = userId(ownerEmail);
        UUID familyId = createdId(call(post("/api/v1/families"), ownerToken, Map.of("name", "Dòng họ thử"))
                .andExpect(status().isCreated()));
        UUID branchA = createdId(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken,
                Map.of("name", "Chi A")).andExpect(status().isCreated()));
        UUID childBranch = createdId(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken,
                Map.of("name", "Chi A1", "parentBranchId", branchA)).andExpect(status().isCreated()));
        UUID branchB = createdId(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken,
                Map.of("name", "Chi B")).andExpect(status().isCreated()));

        String branchAdminEmail = newUser();
        UUID branchAdminId = userId(branchAdminEmail);
        jdbc.update("""
                INSERT INTO member_verification(user_id,family_id,branch_id,role,status)
                VALUES (?,?,?,'BRANCH_ADMIN','APPROVED')
                """, branchAdminId, familyId, branchA);
        String branchAdminToken = token(branchAdminEmail);

        String memberEmail = newUser();
        UUID memberId = userId(memberEmail);
        jdbc.update("""
                INSERT INTO member_verification(user_id,family_id,branch_id,role,status)
                VALUES (?,?,?,'MEMBER','APPROVED')
                """, memberId, familyId, branchA);
        String memberToken = token(memberEmail);

        LocalDate today = LocalDate.now();
        UUID adultA = person(familyId, childBranch, ownerId, "Người lớn A", "MALE",
                LocalDate.of(1960, 1, 1), null, "Hà Nội", false);
        UUID minorA = person(familyId, branchA, ownerId, "Trẻ em A", "FEMALE",
                today.minusYears(10), null, "TP. Hồ Chí Minh", false);
        person(familyId, branchB, ownerId, "Người đã mất B", "OTHER",
                null, 1940, "Huế", true);
        jdbc.update("""
                INSERT INTO parent_child(parent_id,child_id,parent_role,kind,created_by)
                VALUES (?,?,'FATHER','BIOLOGICAL',?)
                """, adultA, minorA, ownerId);

        call(put("/api/v1/directory/members/" + adultA + "/profession-profile"), ownerToken,
                Map.of("jobTitle", "Kỹ sư")).andExpect(status().isCreated());
        call(put("/api/v1/directory/members/" + minorA + "/profession-profile"), ownerToken,
                Map.of("jobTitle", "Học sinh")).andExpect(status().isCreated());

        jdbc.update("""
                INSERT INTO post(family_id,branch_id,content,type,created_by)
                VALUES (?,?,?,'POST',?), (?,?,?,'POST',?)
                """, familyId, branchA, "Bài A", ownerId, familyId, branchB, "Bài B", ownerId);
        OffsetDateTime future = OffsetDateTime.now().plusDays(2);
        jdbc.update("""
                INSERT INTO event(family_id,branch_id,title,start_at,created_by)
                VALUES (?,?,?,?,?), (?,?,?,?,?)
                """, familyId, branchA, "Sự kiện A", future, ownerId,
                familyId, branchB, "Sự kiện B", future.plusHours(1), ownerId);

        call(get("/api/v1/dashboard/statistics"), branchAdminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMembers").value(2))
                .andExpect(jsonPath("$.data.generationStats[0].generation").value(1))
                .andExpect(jsonPath("$.data.generationStats[0].count").value(1))
                .andExpect(jsonPath("$.data.generationStats[1].generation").value(2))
                .andExpect(jsonPath("$.data.ageStats.length()").value(2))
                .andExpect(jsonPath("$.data.ageStats[0].ageRange").value("Dưới 16"))
                .andExpect(jsonPath("$.data.ageStats[0].count").value(1))
                .andExpect(jsonPath("$.data.genderStats.MALE").value(1))
                .andExpect(jsonPath("$.data.genderStats.FEMALE").value(1))
                .andExpect(jsonPath("$.data.professionStats.length()").value(1))
                .andExpect(jsonPath("$.data.professionStats[0].jobTitle").value("Kỹ sư"))
                .andExpect(jsonPath("$.data.locationStats.length()").value(1))
                .andExpect(jsonPath("$.data.locationStats[0].location").value("Hà Nội"))
                .andExpect(jsonPath("$.data.weeklyPosts[7].postCount").value(1))
                .andExpect(jsonPath("$.data.upcomingEvents.length()").value(1))
                .andExpect(jsonPath("$.data.upcomingEvents[0].title").value("Sự kiện A"));

        call(get("/api/v1/dashboard/statistics").param("familyId", familyId.toString())
                        .param("branchId", branchB.toString()), branchAdminToken, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        call(get("/api/v1/dashboard/statistics").param("familyId", familyId.toString()), memberToken, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        call(get("/api/v1/dashboard/statistics").param("familyId", familyId.toString()), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMembers").value(3))
                .andExpect(jsonPath("$.data.genderStats.OTHER").value(1))
                .andExpect(jsonPath("$.data.professionStats.length()").value(1))
                .andExpect(jsonPath("$.data.weeklyPosts[7].postCount").value(2))
                .andExpect(jsonPath("$.data.upcomingEvents.length()").value(2));

        String systemAdminEmail = newUser();
        UUID systemAdminId = userId(systemAdminEmail);
        jdbc.update("INSERT INTO user_role(user_id,role_id) SELECT ?,id FROM role WHERE code='SYSTEM_ADMIN'",
                systemAdminId);
        String systemAdminToken = token(systemAdminEmail);
        call(get("/api/v1/dashboard/statistics").param("familyId", familyId.toString()), systemAdminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMembers").value(3));
        call(get("/api/v1/dashboard/statistics"), systemAdminToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));
    }
}
