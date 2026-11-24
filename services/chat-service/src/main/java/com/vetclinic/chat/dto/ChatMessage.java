package com.vetclinic.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Một tin trong hội thoại. Frontend giữ lịch sử và gửi kèm mỗi lượt (service không lưu gì). */
public record ChatMessage(
        @NotBlank @Pattern(regexp = "user|assistant", message = "role phải là user hoặc assistant") String role,
        // Giới hạn thật (maxMessageChars) kiểm ở ChatController theo cấu hình; đây chỉ là chặn trần cứng.
        @NotBlank @Size(max = 4000) String content
) {
    public boolean fromUser() {
        return "user".equals(role);
    }
}
