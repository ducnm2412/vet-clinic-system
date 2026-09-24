package com.vetclinic.staff.exception;

/** Lỗi nghiệp vụ của staff-service. Thông báo đi thẳng ra màn hình nên viết bằng tiếng Việt. */
public final class StaffExceptions {

    private StaffExceptions() {
    }

    /** Không tìm thấy ca trực / bản ghi chấm công — 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** Thao tác không hợp lệ với trạng thái hiện tại (chưa vào ca, đã ra ca...) — 409. */
    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String message) {
            super(message);
        }
    }

    /** Tham số sai (khoảng ngày ngược, ca kết thúc trước khi bắt đầu...) — 400. */
    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
