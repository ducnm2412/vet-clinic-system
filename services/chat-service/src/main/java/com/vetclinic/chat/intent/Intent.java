package com.vetclinic.chat.intent;

/**
 * Ý định của tin nhắn mới nhất (xem ml/chat-intent/LABELING.md). Ý định có câu soạn sẵn
 * ({@link #hasCannedReply()}) được trả lời ngay, không gọi Gemini; các ý định còn lại đi Gemini,
 * và nhãn được dùng làm gợi ý trong system prompt (gọi công cụ nào, nhắc quy tắc nào).
 */
public enum Intent {
    /** Dấu hiệu nguy hiểm cần đi khám ngay. Câu soạn sẵn cố định, không phụ thuộc mô hình. */
    EMERGENCY(true),
    /** Lạc đề, dò system prompt, đòi bỏ quy tắc, nội dung vi phạm. */
    OFF_TOPIC(true),
    /** Chỉ chào hỏi, không kèm câu hỏi. */
    GREETING(true),
    /** Chỉ cảm ơn / kết thúc. */
    THANKS(true),
    /** Hỏi địa chỉ, hotline, Zalo, email. */
    CONTACT(true),
    /** Hỏi giờ mở/đóng cửa (không phải giờ còn trống để đặt). */
    OPENING_HOURS(true),
    /** Hỏi giá dịch vụ khám chữa → Gemini, gợi ý gọi list_clinic_services. */
    PRICE(false),
    /** Hỏi phòng khám có dịch vụ gì, làm thế nào → Gemini, gợi ý gọi list_clinic_services. */
    SERVICE_INFO(false),
    /** Hỏi/tìm sản phẩm bán ở phòng khám → Gemini, gợi ý gọi search_products. */
    PRODUCT(false),
    /** Đặt/đổi/huỷ lịch, hỏi giờ còn trống → Gemini, gợi ý gọi get_clinic_hours. */
    BOOKING(false),
    /** Chăm sóc / sức khoẻ không khẩn cấp → Gemini, nhắc quy tắc không chẩn đoán, không nêu thuốc. */
    CARE_ADVICE(false),
    /** Để Gemini xử lý (có công cụ tra dữ liệu thật). */
    OTHER(false);

    private final boolean cannedReply;

    Intent(boolean cannedReply) {
        this.cannedReply = cannedReply;
    }

    /** true = trả bằng câu soạn sẵn ({@link CannedReplies#forIntent}), không gọi Gemini. */
    public boolean hasCannedReply() {
        return cannedReply;
    }
}
