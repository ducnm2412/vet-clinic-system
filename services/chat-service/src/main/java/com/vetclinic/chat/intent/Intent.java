package com.vetclinic.chat.intent;

/** Ý định của tin nhắn mới nhất. Mọi ý định trừ OTHER đều được trả bằng câu soạn sẵn, không gọi Gemini. */
public enum Intent {
    EMERGENCY,
    OFF_TOPIC,
    GREETING,
    THANKS,
    CONTACT,
    OPENING_HOURS,
    /** Để Gemini xử lý (có công cụ tra dữ liệu thật). */
    OTHER
}
