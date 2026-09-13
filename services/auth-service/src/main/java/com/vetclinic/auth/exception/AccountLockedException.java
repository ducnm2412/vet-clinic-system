package com.vetclinic.auth.exception;

/**
 * Đăng nhập đúng mật khẩu nhưng tài khoản đã bị khoá — 403.
 *
 * Chỉ ném SAU khi mật khẩu khớp: người không biết mật khẩu vẫn chỉ nhận "Invalid credentials",
 * không dò được email nào đang bị khoá.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account is locked. Please contact the clinic.");
    }
}
