package com.vetclinic.auth.exception;

/**
 * Refresh token không dùng được: không tồn tại, hết hạn, đã thu hồi, hoặc tài khoản không còn
 * hoạt động. Cố ý gộp chung một thông báo — nói rõ lý do chỉ giúp kẻ dò token biết mình đang
 * gần đúng tới đâu.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or has expired");
    }
}
