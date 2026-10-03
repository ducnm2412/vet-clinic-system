package com.vetclinic.auth.exception;

/**
 * Link đặt lại mật khẩu không dùng được: không tồn tại, hết hạn, đã dùng, đã bị thay bằng link
 * mới hơn, hoặc tài khoản không còn hoạt động. Cố ý gộp chung một thông báo.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("Password reset link is invalid or has expired");
    }
}
