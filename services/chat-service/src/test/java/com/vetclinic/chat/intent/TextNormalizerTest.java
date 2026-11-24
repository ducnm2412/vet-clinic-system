package com.vetclinic.chat.intent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void removesDiacriticsAndLowercases() {
        assertThat(TextNormalizer.normalize("Đặt LỊCH khám, Mèo!")).isEqualTo("dat lich kham meo");
    }

    @Test
    void collapsesWhitespaceAndPunctuation() {
        assertThat(TextNormalizer.normalize("  vắc-xin   dại?? ")).isEqualTo("vac xin dai");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }
}
