package com.vetclinic.chat.intent;

import java.util.regex.Pattern;

/**
 * Từ khoá cho biết câu hỏi nói về phòng khám / thú cưng. Dùng để không chặn nhầm một câu hỏi đúng chủ đề
 * chỉ vì mô hình ML (không phải luật regex chính xác) đoán là lạc đề.
 *
 * Cố ý không có "giá", "bao nhiêu", "cho": "giá vàng" và "viết code cho tôi" phải vẫn là lạc đề.
 */
public final class ClinicKeywords {

    private static final Pattern PATTERN = Pattern.compile(
            "\\b(dich vu|san pham|dat lich|dat hen|lich kham|phong kham|thu cung|thu y|bac si|tiem phong|tam spa|sieu am|meo)\\b");

    private ClinicKeywords() {
    }

    public static boolean mentions(String message) {
        return PATTERN.matcher(TextNormalizer.normalize(message)).find();
    }
}
