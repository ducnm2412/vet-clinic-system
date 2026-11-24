package com.vetclinic.chat.dto;

/**
 * @param source rule = câu soạn sẵn (không gọi Gemini), ai = Gemini trả lời,
 *               fallback = Gemini lỗi nên trả câu dự phòng
 */
public record ChatResponse(String reply, String source) {

    public static final String RULE = "rule";
    public static final String AI = "ai";
    public static final String FALLBACK = "fallback";
}
