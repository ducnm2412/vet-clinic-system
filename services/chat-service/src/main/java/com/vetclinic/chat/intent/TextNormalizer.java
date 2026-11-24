package com.vetclinic.chat.intent;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chuẩn hoá tiếng Việt để so khớp rule: bỏ dấu, đ→d, chữ thường, ký tự lạ thành khoảng trắng.
 * Khách hay gõ không dấu ("meo bi co giat") nên rule luôn so trên dạng này.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
