package com.familyconnect.modules.dashboard;

import com.familyconnect.modules.dashboard.service.PdfReportGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Autowired
    private PdfReportGenerator pdfReportGenerator;

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

    private String pdfText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
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

    @Test
    void memberCsvUsesUtf8BomPreventsFormulaInjectionAndRedactsMinorFields() throws Exception {
        String ownerEmail = newUser();
        String ownerToken = token(ownerEmail);
        UUID ownerId = userId(ownerEmail);
        UUID familyId = createdId(call(post("/api/v1/families"), ownerToken, Map.of("name", "Dòng họ CSV"))
                .andExpect(status().isCreated()));

        LocalDate today = LocalDate.now();
        UUID adult = person(familyId, null, ownerId, "=SUM(1,2)", "MALE",
                LocalDate.of(1980, 5, 1), null, "Hà Nội", false);
        UUID child = person(familyId, null, ownerId, "Trẻ em, thử", "FEMALE",
                today.minusYears(10), null, "Đà Nẵng", false);
        jdbc.update("UPDATE person SET user_id=?, current_address='KHONG_XUAT_DIA_CHI' WHERE id=?",
                ownerId, adult);
        jdbc.update("""
                INSERT INTO parent_child(parent_id,child_id,parent_role,kind,created_by)
                VALUES (?,?,'FATHER','BIOLOGICAL',?)
                """, adult, child, ownerId);
        jdbc.update("""
                INSERT INTO profession_profile(family_id,person_id,job_title,work_location,created_by)
                VALUES (?,?,?,?,?), (?,?,?,?,?)
                """, familyId, adult, "Kỹ sư", "Hà Nội", ownerId,
                familyId, child, "Học sinh", "Trường bí mật", ownerId);

        var response = call(get("/api/v1/dashboard/reports/members"), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"members-")))
                .andReturn().getResponse();
        String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF\"id\",\"fullName\",\"birthYear\",\"gender\",\"relationship\","
                + "\"generation\",\"jobTitle\",\"workLocation\"\r\n"));
        assertTrue(csv.contains("\"" + adult + "\",\"'=SUM(1,2)\",\"1980\",\"MALE\",\"cùng một người\""));
        assertTrue(csv.contains("\"" + child + "\",\"Trẻ em, thử\",\"" + today.minusYears(10).getYear()
                + "\",\"\",\"con\",\"\",\"\",\"\""));
        assertFalse(csv.contains("KHONG_XUAT_DIA_CHI"));
        assertFalse(csv.contains("Trường bí mật"));

        call(get("/api/v1/dashboard/reports/members?format=pdf"), ownerToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));

        String memberEmail = newUser();
        UUID memberId = userId(memberEmail);
        jdbc.update("""
                INSERT INTO member_verification(user_id,family_id,role,status)
                VALUES (?,?,'MEMBER','APPROVED')
                """, memberId, familyId);
        call(get("/api/v1/dashboard/reports/members"), token(memberEmail), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    @Test
    void pdfReportsAreScopedAggregatedAuditedAndPaginated() throws Exception {
        String ownerEmail = newUser();
        String ownerToken = token(ownerEmail);
        UUID ownerId = userId(ownerEmail);
        UUID familyId = createdId(call(post("/api/v1/families"), ownerToken, Map.of("name", "Dòng họ PDF"))
                .andExpect(status().isCreated()));
        UUID branchA = createdId(call(post("/api/v1/families/" + familyId + "/branches"), ownerToken,
                Map.of("name", "Chi A")).andExpect(status().isCreated()));
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

        String guestEmail = newUser();
        UUID guestId = userId(guestEmail);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate from = today.minusDays(7);
        OffsetDateTime activityAt = from.plusDays(2).atTime(12, 0)
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();

        UUID privatePerson = person(familyId, branchA, ownerId, "Tên thành viên riêng tư", "MALE",
                LocalDate.of(1980, 5, 1), null, "Hà Nội", false);
        jdbc.update("UPDATE person SET current_address='DIA_CHI_KHONG_DUOC_XUAT' WHERE id=?", privatePerson);
        UUID postId = jdbc.queryForObject("""
                INSERT INTO post(family_id,branch_id,content,type,created_by,created_at)
                VALUES (?,?,?,'POST',?,?) RETURNING id
                """, UUID.class, familyId, branchA, "Nội dung không đưa vào báo cáo", ownerId, activityAt);
        jdbc.update("""
                INSERT INTO comment(post_id,content,created_by,created_at)
                VALUES (?, 'Bình luận', ?, ?)
                """, postId, ownerId, activityAt.plusHours(1));
        jdbc.update("""
                INSERT INTO reaction(post_id,type,created_by,created_at)
                VALUES (?, 'LIKE', ?, ?)
                """, postId, ownerId, activityAt.plusHours(2));

        UUID eventA = jdbc.queryForObject("""
                INSERT INTO event(family_id,branch_id,title,start_at,created_by)
                VALUES (?,?,?,?,?) RETURNING id
                """, UUID.class, familyId, branchA, "Sự kiện tiếng Việt A", activityAt, ownerId);
        jdbc.update("""
                INSERT INTO event_participant(event_id,user_id,rsvp_status,created_by)
                VALUES (?,?,'GOING',?), (?,?,'GOING',?), (?,?,'NOT_GOING',?)
                """, eventA, ownerId, ownerId, eventA, memberId, memberId, eventA, guestId, guestId);
        UUID ownerParticipantId = jdbc.queryForObject("""
                SELECT id FROM event_participant WHERE event_id=? AND user_id=?
                """, UUID.class, eventA, ownerId);
        call(put("/api/v1/events/" + eventA + "/participants/" + ownerParticipantId + "/attendance"),
                ownerToken, Map.of("attended", true))
                .andExpect(status().isOk());
        UUID eventOutsideScope = jdbc.queryForObject("""
                INSERT INTO event(family_id,branch_id,title,start_at,created_by)
                VALUES (?,?,?,?,?) RETURNING id
                """, UUID.class, familyId, branchB, "Sự kiện ngoài phạm vi", activityAt, ownerId);
        UUID familyWideEvent = jdbc.queryForObject("""
                INSERT INTO event(family_id,title,start_at,created_by)
                VALUES (?,?,?,?) RETURNING id
                """, UUID.class, familyId, "Sự kiện toàn gia đình", activityAt, ownerId);
        UUID nonexistentParticipant = UUID.randomUUID();
        call(put("/api/v1/events/" + eventOutsideScope + "/participants/"
                        + nonexistentParticipant + "/attendance"),
                branchAdminToken, Map.of("attended", true)).andExpect(status().isForbidden());
        call(put("/api/v1/events/" + familyWideEvent + "/participants/"
                        + nonexistentParticipant + "/attendance"),
                branchAdminToken, Map.of("attended", true)).andExpect(status().isForbidden());

        String query = "?familyId=" + familyId + "&from=" + from + "&to=" + today;
        var genealogy = call(get("/api/v1/dashboard/reports/GENEALOGY/pdf" + query), ownerToken, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        String genealogyText = pdfText(genealogy);
        assertTrue(genealogyText.contains("Báo cáo gia phả"));
        assertTrue(genealogyText.contains("Thành viên theo thế hệ"));
        assertFalse(genealogyText.contains("Tên thành viên riêng tư"));
        assertFalse(genealogyText.contains("DIA_CHI_KHONG_DUOC_XUAT"));
        // Phạm vi in tên gia đình, không in UUID.
        assertTrue(genealogyText.replaceAll("\\s+", " ").contains("Dòng họ PDF"));
        assertFalse(genealogyText.contains(familyId.toString()));

        var community = call(get("/api/v1/dashboard/reports/COMMUNITY/pdf" + query), ownerToken, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String communityText = pdfText(community);
        assertTrue(communityText.contains("Bài đăng theo tuần"));
        assertTrue(communityText.contains("Bình luận"));
        assertTrue(communityText.contains("Reaction"));
        assertFalse(communityText.contains("Nội dung không đưa vào báo cáo"));

        var events = call(get("/api/v1/dashboard/reports/EVENTS/pdf" + query), ownerToken, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String eventText = pdfText(events).replaceAll("\\s+", " ");
        assertTrue(eventText.contains("Sự kiện tiếng Việt A"));
        assertTrue(eventText.contains("50.0%"));
        assertTrue(eventText.contains("Tỷ lệ điểm danh trong nhóm GOING"));
        assertTrue(eventText.contains("Tổng số lượt điểm danh thực tế"));
        assertFalse(eventText.contains("DIA_CHI_KHONG_DUOC_XUAT"));

        var branchEvents = call(get("/api/v1/dashboard/reports/EVENTS/pdf" + query),
                branchAdminToken, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String branchEventText = pdfText(branchEvents).replaceAll("\\s+", " ");
        assertTrue(branchEventText.contains("Sự kiện tiếng Việt A"));
        assertFalse(branchEventText.contains("Sự kiện ngoài phạm vi"));
        call(get("/api/v1/dashboard/reports/EVENTS/pdf" + query + "&branchId=" + branchB),
                branchAdminToken, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(get("/api/v1/dashboard/reports/EVENTS/pdf" + query), memberToken, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        assertTrue(jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE actor_user_id=? AND action='STATISTICAL_REPORT_PDF_EXPORTED'
                """, Integer.class, ownerId) >= 3);

        List<List<String>> longRows = IntStream.range(0, 100)
                .mapToObj(index -> List.of("Dòng dữ liệu " + index, Integer.toString(index)))
                .toList();
        byte[] longPdf = pdfReportGenerator.generate("Báo cáo dài", "Toàn gia đình", "01/01/2026 - 31/01/2026",
                OffsetDateTime.now(), "Người tạo", List.of(new PdfReportGenerator.Section(
                        "Bảng dài", List.of("Nội dung", "Số lượng"), longRows, false)));
        try (PDDocument document = Loader.loadPDF(longPdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(new PDFTextStripper().getText(document).contains("Trang 2 /"));
        }
    }

    @Test
    void pdfReportsHandleEmptyDatasets() throws Exception {
        String ownerEmail = newUser();
        String ownerToken = token(ownerEmail);
        UUID familyId = createdId(call(post("/api/v1/families"), ownerToken, Map.of("name", "Dòng họ trống"))
                .andExpect(status().isCreated()));
        LocalDate to = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String query = "?familyId=" + familyId + "&from=" + to.minusDays(13) + "&to=" + to;

        for (String reportType : List.of("GENEALOGY", "COMMUNITY", "EVENTS")) {
            byte[] pdf = call(get("/api/v1/dashboard/reports/" + reportType + "/pdf" + query),
                    ownerToken, null)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertTrue(pdfText(pdf).contains("Báo cáo"));
        }
        // BR-DSH-03: khoảng ngày quá 366 ngày bị từ chối, không ghi audit.
        call(get("/api/v1/dashboard/reports/COMMUNITY/pdf?familyId=" + familyId
                + "&from=" + to.minusDays(366) + "&to=" + to), ownerToken, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));
        assertEquals(3, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE action='STATISTICAL_REPORT_PDF_EXPORTED'
                """, Integer.class));
    }
}
