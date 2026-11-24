package com.vetclinic.chat.exception;

/** Chưa cấu hình GEMINI_API_KEY nên chatbot không trả lời được các câu cần AI. */
public class ChatDisabledException extends RuntimeException {
    public ChatDisabledException() {
        super("Chatbot chưa được bật.");
    }
}
