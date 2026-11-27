package com.familyconnect.modules.ai.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatLanguageModelTest {
    private static com.fasterxml.jackson.databind.JsonNode json(String raw) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
    }

    @Test void truncatedAnswerIsAnErrorNotAHalfSentence() throws Exception {
        var response = json("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"An làm nghề bác\"}]}}]}");
        assertThrows(IllegalStateException.class, () -> ChatLanguageModel.textOf(response));
    }

    @Test void answerSplitAcrossPartsIsJoinedAndThoughtsAreSkipped() throws Exception {
        var response = json("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":["
                + "{\"text\":\"đang nghĩ\",\"thought\":true},{\"text\":\"Hà là em họ của An,\"},"
                + "{\"text\":\" vì hai người cùng ông nội.\"}]}}]}");
        assertEquals("Hà là em họ của An, vì hai người cùng ông nội.", ChatLanguageModel.textOf(response));
    }

    @Test void completeAnswerIsReturned() throws Exception {
        var response = json("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\" An làm bác sĩ. \"}]}}]}");
        assertEquals("An làm bác sĩ.", ChatLanguageModel.textOf(response));
        assertEquals(2048, ChatLanguageModel.generationConfig("gemini-3.5-flash-lite").get("maxOutputTokens"));
    }

    @Test void gemini3UsesThinkingLevelWithoutTemperature() {
        var config = ChatLanguageModel.generationConfig("gemini-3.5-flash-lite");
        assertTrue(config.containsKey("thinkingConfig"));
        assertFalse(config.containsKey("temperature"));
    }

    @Test void olderModelsKeepLowTemperatureWithoutThinkingLevel() {
        var config = ChatLanguageModel.generationConfig("gemini-2.5-flash");
        assertEquals(0.1, config.get("temperature"));
        assertFalse(config.containsKey("thinkingConfig"));
    }
}
