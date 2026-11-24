package com.vetclinic.chat.service;

/**
 * Người đang chat.
 *
 * @param rateKey     khoá đếm hạn mức: "user:{id}" nếu đã đăng nhập, "ip:{địa chỉ}" nếu vãng lai
 * @param loggedIn    token hợp lệ (JwtAuthFilter đã xác thực)
 * @param bearerToken token gốc của khách, chỉ có khi loggedIn; dùng để gọi API cần đăng nhập
 *                    (giờ khám còn trống). Không bao giờ ghi log hay gửi cho Gemini.
 */
public record Caller(String rateKey, boolean loggedIn, String bearerToken) {

    public static Caller guest(String ip) {
        return new Caller("ip:" + ip, false, null);
    }

    public static Caller user(String userId, String bearerToken) {
        return new Caller("user:" + userId, true, bearerToken);
    }
}
