package com.vetclinic.chat.exception;

/** Mọi model Gemini đều lỗi/timeout/hết quota. Message không chứa API key hay nội dung hội thoại. */
public class GeminiUnavailableException extends RuntimeException {
    public GeminiUnavailableException(String message) {
        super(message);
    }
}
