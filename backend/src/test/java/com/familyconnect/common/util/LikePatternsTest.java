package com.familyconnect.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @Test
    void wrapsValueAndEscapesLikeWildcards() {
        assertThat(LikePatterns.contains("tổ")).isEqualTo("%tổ%");
        assertThat(LikePatterns.contains("50%_off!")).isEqualTo("%50!%!_off!!%");
        assertThat(LikePatterns.contains("")).isEqualTo("%%");
        assertThat(LikePatterns.contains(null)).isNull();
    }
}
