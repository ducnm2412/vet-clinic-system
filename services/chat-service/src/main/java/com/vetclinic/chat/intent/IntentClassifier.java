package com.vetclinic.chat.intent;

/**
 * Nhận diện ý định của tin nhắn. Có RuleIntentClassifier (regex trên chữ đã bỏ dấu),
 * MlIntentClassifier (TF-IDF + hồi quy logistic) và CompositeIntentClassifier (bean chính,
 * kết hợp hai cái trên theo app.chat.intent.mode).
 */
public interface IntentClassifier {

    Intent classify(String message);

    /**
     * Như {@link #classify(String)}, nhưng {@code allowRemote = false} cấm gọi dịch vụ ngoài (tầng A2 gọi Gemini
     * embedContent) — dùng khi nhãn gần như không ảnh hưởng câu trả lời, để không chờ mạng vô ích.
     */
    default Intent classify(String message, boolean allowRemote) {
        return classify(message);
    }
}
