package com.vetclinic.auth.exception;

/**
 * Không đổi được mật khẩu vì dữ liệu người dùng nhập (mật khẩu hiện tại sai, mật khẩu mới trùng
 * mật khẩu cũ, tài khoản chưa có mật khẩu). Trả 400 chứ không phải 401: frontend coi 401 là hết
 * phiên và sẽ tự làm mới hoặc đăng xuất, trong khi phiên vẫn còn tốt.
 *
 * {@code field} là ô nhập bị lỗi (để frontend hiện ngay dưới ô đó); null nếu lỗi không thuộc ô nào.
 */
public class PasswordChangeException extends RuntimeException {

    private final String field;

    public PasswordChangeException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
