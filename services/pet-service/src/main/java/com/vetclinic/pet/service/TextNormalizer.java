package com.vetclinic.pet.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chuẩn hoá chữ để so khớp và tìm kiếm không phân biệt dấu, hoa/thường. Dùng cho danh mục thuốc
 * và tên đơn mẫu — cột {@code search_name} của hai bảng đó.
 */
final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Bỏ dấu, hạ chữ thường, gộp khoảng trắng. "Đau  Bụng" → "dau bung". Null → chuỗi rỗng. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD);
        return decomposed
                .replaceAll("\\p{M}+", "")
                // đ/Đ không phân rã được thành d + dấu nên phải đổi tay.
                .replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    /** Escape ký tự đại diện của LIKE ({@code \}, {@code %}, {@code _}) để từ khoá được hiểu là chữ thường. */
    static String escapeLike(String normalized) {
        return normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
