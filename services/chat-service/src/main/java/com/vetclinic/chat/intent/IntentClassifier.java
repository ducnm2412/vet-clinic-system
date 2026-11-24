package com.vetclinic.chat.intent;

/**
 * Nhận diện ý định của tin nhắn. Có RuleIntentClassifier (regex trên chữ đã bỏ dấu),
 * MlIntentClassifier (TF-IDF + hồi quy logistic) và CompositeIntentClassifier (bean chính,
 * kết hợp hai cái trên theo app.chat.intent.mode).
 */
public interface IntentClassifier {

    Intent classify(String message);
}
