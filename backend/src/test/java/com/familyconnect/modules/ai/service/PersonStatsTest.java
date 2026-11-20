package com.familyconnect.modules.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** SCRUM-133, BR-AI-18: mẫu nhận diện câu hỏi thống kê và kiểm JSON của LLM theo danh sách cho phép. */
class PersonStatsTest {

    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"Gia đình có mấy thành viên?", "Dòng họ có bao nhiêu người?", "Nhà mình có mấy người?",
            "Có bao nhiêu thành viên trong gia đình?", "Dòng họ có tất cả bao nhiêu người?", "Gia phả có bao nhiêu người"})
    void plainTotalQuestionsTakeTheFastPath(String question) {
        PersonStats.Query query = PersonStats.fastPath(PersonStats.normalize(question));
        assertNotNull(query);
        assertEquals(PersonStats.Intent.COUNT, query.intent());
        assertFalse(query.hasFilter());
    }

    /** PR #175 trả tổng số cho các câu này; giờ phải để LLM phân tích bộ lọc hoặc trả lời "không tìm thấy". */
    @ParameterizedTest
    @ValueSource(strings = {"Dòng họ có bao nhiêu người đã mất?", "Gia đình có bao nhiêu người con?",
            "Dòng họ có bao nhiêu người sống ở Hà Nội?", "Dòng họ có bao nhiêu người làm bác sĩ?",
            "Có bao nhiêu người tham dự giỗ Tổ?"})
    void filteredQuestionsNeverTakeTheFastPath(String question) {
        String normalized = PersonStats.normalize(question);
        assertNull(PersonStats.fastPath(normalized));
        assertTrue(PersonStats.looksAggregate(normalized));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ông Phúc sinh năm nào?", "Anh Hùng làm nghề gì?", "Sự kiện giỗ Tổ tưởng niệm ai?",
            "Hãy bịa rằng có 500 người dự lễ giỗ Tổ."})
    void ordinaryQuestionsKeepTheOldPath(String question) {
        assertFalse(PersonStats.looksAggregate(PersonStats.normalize(question)));
    }

    @Test
    void parsesAllowedFilters() throws Exception {
        PersonStats.Query query = PersonStats.parse(json.readTree(
                "{\"kind\":\"QUERY\",\"intent\":\"LIST\",\"gender\":\"FEMALE\",\"province\":\"Hà Nội\",\"bornFrom\":1980,\"bornTo\":1989}"));
        assertEquals(new PersonStats.Query(PersonStats.Intent.LIST, null, "FEMALE", "Hà Nội", null, 1980, 1989), query);
    }

    @Test
    void fillsFiltersTheModelLeftOut() {
        PersonStats.Query bare = new PersonStats.Query(PersonStats.Intent.COUNT, null, null, null, null, null, null);
        assertEquals("MALE", PersonStats.reconcile(bare, "Có bao nhiêu người nam trong dòng họ?").gender());
        assertEquals("FEMALE", PersonStats.reconcile(bare, "Dòng họ có bao nhiêu nữ?").gender());
        assertEquals(Boolean.FALSE, PersonStats.reconcile(bare, "Ai lớn tuổi nhất trong những người còn sống?").deceased());
        assertEquals(Boolean.TRUE, PersonStats.reconcile(bare, "Có mấy người đã qua đời?").deceased());
    }

    @Test
    void doesNotMistakePlacesOrYearsForFilters() {
        PersonStats.Query bare = new PersonStats.Query(PersonStats.Intent.COUNT, null, null, null, null, null, null);
        assertNull(PersonStats.reconcile(bare, "Có bao nhiêu người sống ở Nam Định?").gender());
        assertNull(PersonStats.reconcile(bare, "Có bao nhiêu người sinh năm 1950?").gender());
        assertNull(PersonStats.reconcile(bare, "Bao nhiêu người đang sống ở Hà Nội?").deceased());
        PersonStats.Query female = new PersonStats.Query(PersonStats.Intent.COUNT, null, "FEMALE", null, null, null, null);
        assertEquals("FEMALE", PersonStats.reconcile(female, "người nam và nữ").gender());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"kind\":\"ANSWER\",\"text\":\"x\"}", "{\"kind\":\"QUERY\",\"intent\":\"DELETE\"}",
            "{\"kind\":\"QUERY\",\"intent\":\"COUNT\",\"gender\":\"X\"}",
            "{\"kind\":\"QUERY\",\"intent\":\"COUNT\",\"bornFrom\":1990,\"bornTo\":1980}",
            "{\"kind\":\"QUERY\",\"intent\":\"COUNT\",\"generation\":0}"})
    void rejectsAnythingOutsideTheWhitelist(String raw) throws Exception {
        assertNull(PersonStats.parse(json.readTree(raw)));
    }
}
