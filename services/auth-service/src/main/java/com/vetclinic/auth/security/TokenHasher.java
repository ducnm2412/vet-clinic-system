package com.vetclinic.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Sinh token ngẫu nhiên và băm SHA-256 — dùng chung cho mọi token chỉ lưu bản băm ở DB. */
public final class TokenHasher {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenHasher() {
    }

    public static String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Mọi JVM đều bắt buộc có SHA-256; tới được đây là môi trường hỏng, không phải lỗi dữ liệu.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
