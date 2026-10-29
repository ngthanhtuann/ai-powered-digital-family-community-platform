package com.familyconnect.modules.events;

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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Sự kiện (UC-EVT-01..03) qua HTTP với PostgreSQL thật; mỗi test tự rollback, dữ liệu giả.
 * Bao phủ nhóm test bắt buộc: phân quyền, IDOR giữa gia đình, phạm vi chi (BR-COM-03), quy ước API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "event." + UUID.randomUUID() + "@example.com";
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

    /** Chủ gia đình mới (BRANCH_ADMIN toàn gia đình), trả về [token, familyId]. */
    private Object[] owner() throws Exception {
        String token = token(newUser());
        UUID familyId = id(call(post("/api/v1/families"), token, Map.of("name", "Dòng họ thử"))
                .andExpect(status().isCreated()));
        return new Object[]{token, familyId};
    }

    private UUID branch(String ownerToken, UUID familyId, String name) throws Exception {
        return id(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken, Map.of("name", name))
                .andExpect(status().isCreated()));
    }

    /** Thành viên đã duyệt; trả về [token, userId]. */
    private Object[] member(UUID familyId, UUID branchId) throws Exception {
        String email = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) values (?, ?, ?, 'MEMBER', 'APPROVED')",
                userId(email), familyId, branchId);
        return new Object[]{token(email), userId(email)};
    }

    private static Map<String, Object> event(String title, OffsetDateTime start) {
        return Map.of("title", title, "startAt", start.toString());
    }

    @Test
    void membersCreateRsvpAndCountsAreCorrect() throws Exception {
        Object[] a = owner();
        String owner = (String) a[0];
        UUID family = (UUID) a[1];
        String m1 = (String) member(family, null)[0];
        String m2 = (String) member(family, null)[0];
        String unverified = token(newUser());
        OffsetDateTime future = OffsetDateTime.now().plusDays(3);

        UUID eventId = id(call(post("/api/v1/events"), m1, Map.of("title", "Giỗ tổ", "startAt", future.toString(),
                "location", "Nhà thờ họ")).andExpect(status().isCreated()));
        call(post("/api/v1/events"), unverified, event("x", future))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_002"));
        call(post("/api/v1/events"), m1, Map.of("title", "x", "startAt", future.toString(),
                "endAt", future.minusDays(1).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));

        call(put("/api/v1/events/" + eventId + "/rsvp"), owner, Map.of("rsvpStatus", "GOING")).andExpect(status().isOk());
        call(put("/api/v1/events/" + eventId + "/rsvp"), m1, Map.of("rsvpStatus", "GOING")).andExpect(status().isOk());
        call(put("/api/v1/events/" + eventId + "/rsvp"), m2, Map.of("rsvpStatus", "MAYBE")).andExpect(status().isOk());
        call(put("/api/v1/events/" + eventId + "/rsvp"), m2, Map.of("rsvpStatus", "NOT_GOING")).andExpect(status().isOk());
        call(put("/api/v1/events/" + eventId + "/rsvp"), m2, Map.of("rsvpStatus", "YES")).andExpect(status().isBadRequest());

        call(get("/api/v1/events/" + eventId), m1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rsvpCounts.going").value(2))
                .andExpect(jsonPath("$.data.rsvpCounts.notGoing").value(1))
                .andExpect(jsonPath("$.data.rsvpCounts.maybe").value(0))
                .andExpect(jsonPath("$.data.myRsvpStatus").value("GOING"));
        call(get("/api/v1/events/" + eventId + "/participants"), m2, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(content().string(not(containsString("@example.com"))));

        // EVT_001: RSVP khi sự kiện đã bắt đầu
        jdbc.update("update event set start_at = now() - interval '1 hour' where id = ?", eventId);
        call(put("/api/v1/events/" + eventId + "/rsvp"), m1, Map.of("rsvpStatus", "MAYBE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EVT_001"));
    }

    @Test
    void onlyCreatorOrAdminManagesEventsAndParticipants() throws Exception {
        Object[] a = owner();
        String owner = (String) a[0];
        UUID family = (UUID) a[1];
        String creator = (String) member(family, null)[0];
        Object[] other = member(family, null);
        UUID eventId = id(call(post("/api/v1/events"), creator, event("Họp mặt", OffsetDateTime.now().plusDays(2)))
                .andExpect(status().isCreated()));

        call(patch("/api/v1/events/" + eventId), (String) other[0], Map.of("title", "Sửa trái phép"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(delete("/api/v1/events/" + eventId), (String) other[0], null).andExpect(status().isForbidden());
        call(patch("/api/v1/events/" + eventId), creator, Map.of("location", "Đình làng"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.location").value("Đình làng"))
                .andExpect(jsonPath("$.data.title").value("Họp mặt"));

        UUID participant = id(call(post("/api/v1/events/" + eventId + "/participants"), creator,
                Map.of("userId", other[1].toString())).andExpect(status().isCreated()));
        call(delete("/api/v1/participants/" + participant), creator, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        // Đã gỡ (xóa mềm) thì thêm lại được, nhờ chỉ mục duy nhất có điều kiện deleted_at IS NULL.
        call(post("/api/v1/events/" + eventId + "/participants"), creator, Map.of("userId", other[1].toString()))
                .andExpect(status().isCreated());

        call(delete("/api/v1/events/" + eventId), owner, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        call(get("/api/v1/events/" + eventId), owner, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND_001"));
        call(get("/api/v1/events?size=101"), owner, null).andExpect(status().isBadRequest());
    }

    @Test
    void addingSameParticipantTwiceReturnsConflict() throws Exception {
        Object[] a = owner();
        UUID family = (UUID) a[1];
        String creator = (String) member(family, null)[0];
        Object[] other = member(family, null);
        UUID eventId = id(call(post("/api/v1/events"), creator, event("Họp mặt", OffsetDateTime.now().plusDays(2)))
                .andExpect(status().isCreated()));
        call(post("/api/v1/events/" + eventId + "/participants"), creator, Map.of("userId", other[1].toString()))
                .andExpect(status().isCreated());
        // Lỗi trùng khóa làm hỏng giao dịch của test, nên đây phải là lời gọi cuối.
        call(post("/api/v1/events/" + eventId + "/participants"), creator, Map.of("userId", other[1].toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT_001"));
    }

    @Test
    void branchEventsFollowBranchScopeAndOtherFamilyIsBlocked() throws Exception {
        Object[] a = owner();
        String owner = (String) a[0];
        UUID family = (UUID) a[1];
        UUID b1 = branch(owner, family, "Chi Một");
        UUID b2 = branch(owner, family, "Chi Hai");
        String inB1 = (String) member(family, b1)[0];
        String inB2 = (String) member(family, b2)[0];
        OffsetDateTime future = OffsetDateTime.now().plusDays(5);
        UUID familyEvent = id(call(post("/api/v1/events"), inB1, event("Cả họ", future)).andExpect(status().isCreated()));
        UUID branchEvent = id(call(post("/api/v1/events"), inB1, Map.of("title", "Họp chi Một",
                "startAt", future.toString(), "branchId", b1.toString())).andExpect(status().isCreated()));

        call(get("/api/v1/events/" + branchEvent), inB2, null).andExpect(status().isForbidden());
        call(get("/api/v1/events"), inB2, null)
                .andExpect(jsonPath("$.data[*].id", hasItem(familyEvent.toString())))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(branchEvent.toString()))));
        call(get("/api/v1/events"), owner, null)
                .andExpect(jsonPath("$.data[*].id", hasItem(branchEvent.toString())));
        call(post("/api/v1/events"), inB1, Map.of("title", "Chi lạ", "startAt", future.toString(), "branchId", b2.toString()))
                .andExpect(status().isForbidden());

        String otherFamily = (String) owner()[0];
        call(get("/api/v1/events/" + familyEvent), otherFamily, null).andExpect(status().isForbidden());
        call(put("/api/v1/events/" + familyEvent + "/rsvp"), otherFamily, Map.of("rsvpStatus", "GOING"))
                .andExpect(status().isForbidden());
        call(get("/api/v1/events?familyId=" + family), otherFamily, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
    }
}
