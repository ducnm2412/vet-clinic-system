package com.vetclinic.chat.dto;

/**
 * @param enabled   chatbot luôn mở (tầng rule không cần Gemini); frontend hiện nút chat khi true
 * @param aiEnabled đã có GEMINI_API_KEY, câu hỏi phức tạp mới được Gemini trả lời
 */
public record ChatStatusResponse(boolean enabled, boolean aiEnabled) {
}
