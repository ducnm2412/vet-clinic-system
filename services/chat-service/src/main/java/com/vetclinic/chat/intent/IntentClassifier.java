package com.vetclinic.chat.intent;

/**
 * Nhận diện ý định của tin nhắn. Hiện là RuleIntentClassifier (regex trên chữ đã bỏ dấu); chỗ này
 * để sau cắm bộ phân loại ML mà ChatService không phải sửa.
 */
public interface IntentClassifier {

    Intent classify(String message);
}
