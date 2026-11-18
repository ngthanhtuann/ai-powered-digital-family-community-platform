package com.familyconnect.modules.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.familyconnect.modules.ai.dto.ChatResponse.SourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatCitationTest {

    private final SourceReference person = new SourceReference("PERSON", UUID.randomUUID());
    private final SourceReference event = new SourceReference("EVENT", UUID.randomUUID());
    private final List<SourceReference> available = List.of(person, event);

    @Test
    void dropsCitationCutOffAtEndOfAnswer() {
        // Gemini hết token giữa thẻ nguồn (SCRUM-125): bỏ phần thẻ dở dang, không hiện cho người dùng.
        var cited = ChatService.citations("Bạn là cháu nội của Nguyễn Văn Tổ [PERSON 15f3f400-def0-4cd9", available);
        assertEquals("Bạn là cháu nội của Nguyễn Văn Tổ", cited.answer());
    }

    @Test
    void stripsBracketCitationAndKeepsOnlyCitedSource() {
        var cited = ChatService.citations("Nguyễn Văn Hùng ([PERSON " + person.sourceId() + "]).", available);
        assertEquals("Nguyễn Văn Hùng.", cited.answer());
        assertEquals(List.of(person), cited.sources());
    }

    @Test
    void stripsVietnameseSourceLabelAndSeveralIds() {
        var cited = ChatService.citations("Giỗ Tổ ngày 20/12/2026 (Nguồn: EVENT " + event.sourceId()
                + "). Cụ Tổ sinh năm 1925 [PERSON " + person.sourceId() + ", EVENT " + event.sourceId() + "].", available);
        assertEquals("Giỗ Tổ ngày 20/12/2026. Cụ Tổ sinh năm 1925.", cited.answer());
        assertEquals(available, cited.sources());
    }

    @Test
    void keepsOrdinaryParenthesesAndAllSourcesWhenNothingCited() {
        var cited = ChatService.citations("Ông Phúc (con trưởng) sinh năm 1950.", available);
        assertEquals("Ông Phúc (con trưởng) sinh năm 1950.", cited.answer());
        assertEquals(available, cited.sources());
    }

    @Test
    void ignoresIdsOutsideRetrievedContext() {
        var cited = ChatService.citations("Có [POST " + UUID.randomUUID() + "].", available);
        assertEquals("Có.", cited.answer());
        assertEquals(available, cited.sources());
    }

    @Test
    void notFoundAnswerHasNoSources() {
        var cited = ChatService.citations("Không tìm thấy thông tin.", available);
        assertEquals("Không tìm thấy thông tin.", cited.answer());
        assertEquals(List.of(), cited.sources());
    }
}
