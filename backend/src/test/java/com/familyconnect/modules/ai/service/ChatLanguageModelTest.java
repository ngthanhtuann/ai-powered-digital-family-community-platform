package com.familyconnect.modules.ai.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatLanguageModelTest {
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
