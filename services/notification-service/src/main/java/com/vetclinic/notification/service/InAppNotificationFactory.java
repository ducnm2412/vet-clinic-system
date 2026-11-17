package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentNotificationEvent;
import com.vetclinic.notification.messaging.OrderStatusChangedEvent;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Soạn nội dung thông báo trong app từ sự kiện lịch hẹn. Ngắn hơn email: chuông chỉ cần một
 * tiêu đề, một hai câu, và đường dẫn tới trang lịch để khách bấm vào xử lý.
 *
 * Văn bản thuần (không HTML): frontend hiển thị dạng text nên tên bé do khách gõ không thể chèn mã.
 */
public final class InAppNotificationFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private InAppNotificationFactory() {
    }

    /** Nội dung đã soạn. */
    public record Content(String title, String message, String link) {
    }

    /** Rỗng nếu loại sự kiện không biết (booking-service mới hơn bản này) — bỏ qua chứ không lỗi. */
    public static Optional<Content> forAppointment(AppointmentNotificationEvent event) {
        String pet = event.petName() == null || event.petName().isBlank() ? "thú cưng của bạn" : event.petName().trim();
        String doctor = event.doctorName() == null || event.doctorName().isBlank() ? null : event.doctorName().trim();
        String when = when(event.date(), event.startTime());
        String link = event.appointmentId() == null ? null : "/appointments/" + event.appointmentId();
        String type = event.type() == null ? "" : event.type();

        return switch (type) {
            case "REQUEST_RECEIVED" -> Optional.of(new Content(
                    "Đã nhận yêu cầu đặt lịch cho " + pet,
                    "Phòng khám đã nhận yêu cầu" + at(when) + ". Đây chưa phải lịch đã chốt — "
                            + "phòng khám sẽ xếp bác sĩ rảnh và báo lại cho bạn.",
                    link));
            case "CONFIRMED" -> Optional.of(new Content(
                    "Lịch khám của " + pet + " đã được xác nhận",
                    "Lịch khám" + at(when) + (doctor == null ? "" : ", bác sĩ " + doctor) + " đã được xác nhận.",
                    link));
            case "PROPOSAL" -> Optional.of(new Content(
                    "Phòng khám đề xuất giờ khác cho " + pet,
                    "Giờ bạn chọn" + preferred(event) + " chưa xếp được bác sĩ. Phòng khám đề xuất" + at(when)
                            + (doctor == null ? "" : ", bác sĩ " + doctor) + ". Bạn cần đồng ý hoặc huỷ lịch; "
                            + "phòng khám đang giữ chỗ này cho bạn.",
                    link));
            case "CANCELLED_BY_CLINIC" -> Optional.of(new Content(
                    "Lịch khám của " + pet + " đã bị huỷ",
                    "Phòng khám đã huỷ lịch khám" + at(when) + ". Bạn có thể gửi yêu cầu đặt lịch mới.",
                    "/appointments/create"));
            default -> Optional.empty();
        };
    }

    /** Thông báo cho khách khi đơn hàng đổi trạng thái. Rỗng với trạng thái không biết (ví dụ PENDING). */
    public static Optional<Content> forOrder(OrderStatusChangedEvent event) {
        String code = event.orderCode() == null || event.orderCode().isBlank() ? "của bạn" : event.orderCode().trim();
        String link = event.orderId() == null ? "/user/orders" : "/user/orders/" + event.orderId();
        String status = event.status() == null ? "" : event.status();

        return switch (status) {
            case "CONFIRMED" -> Optional.of(new Content(
                    "Đơn hàng " + code + " đã được xác nhận",
                    "Cửa hàng đã xác nhận đơn và đang chuẩn bị hàng cho bạn.",
                    link));
            case "SHIPPING" -> Optional.of(new Content(
                    "Đơn hàng " + code + " đang được giao",
                    "Đơn đang trên đường giao đến bạn"
                            + ("COD".equals(event.paymentMethod()) ? ", bạn thanh toán khi nhận hàng" : "")
                            + ". Nhận được hàng, bạn nhớ bấm \"Đã nhận hàng\" trong chi tiết đơn nhé.",
                    link));
            case "COMPLETED" -> Optional.of(new Content(
                    "Đơn hàng " + code + " đã giao xong",
                    "Đơn đã hoàn tất. Cảm ơn bạn đã mua hàng tại VetClinic.",
                    link));
            case "CANCELLED" -> Optional.of(new Content(
                    "Đơn hàng " + code + " đã bị huỷ",
                    "Đơn đã bị huỷ" + reason(event) + "."
                            + (event.refundNeeded() ? " Cửa hàng sẽ hoàn lại tiền bạn đã thanh toán." : ""),
                    link));
            default -> Optional.empty();
        };
    }

    private static String reason(OrderStatusChangedEvent event) {
        return event.cancelReason() == null || event.cancelReason().isBlank()
                ? "" : " (lý do: " + event.cancelReason().trim() + ")";
    }

    private static String when(java.time.LocalDate date, java.time.LocalTime time) {
        if (date == null || time == null) {
            return "";
        }
        return TIME_FORMAT.format(time) + " ngày " + DATE_FORMAT.format(date);
    }

    private static String at(String when) {
        return when.isEmpty() ? "" : " lúc " + when;
    }

    private static String preferred(AppointmentNotificationEvent event) {
        String when = when(event.preferredDate(), event.preferredTime());
        return when.isEmpty() ? "" : " (" + when + ")";
    }
}
