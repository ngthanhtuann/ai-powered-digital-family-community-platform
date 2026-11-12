package com.familyconnect.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void acceptsExactWebOrigins() {
        assertThat(config.corsConfigurationSource("https://web.example.com,http://localhost:3000"))
                .isNotNull();
    }

    @Test
    void rejectsWildcardPathsAndMissingOrigins() {
        for (String invalid : new String[]{"*", "https://web.example.com/path", "", "https://web.example.com?x=1"}) {
            assertThatThrownBy(() -> config.corsConfigurationSource(invalid))
                    .as("CORS origin không hợp lệ: %s", invalid)
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
