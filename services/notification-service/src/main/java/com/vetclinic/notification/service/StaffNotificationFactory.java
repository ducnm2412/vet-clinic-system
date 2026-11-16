package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentNotificationEvent;
import com.vetclinic.notification.messaging.OrderPlacedEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Soạn thông báo trong app cho phía phòng khám (bác sĩ, admin, nhân viên quầy). Mỗi hàm trả rỗng khi
 * sự kiện không có gì để báo cho nhóm đó — người gọi bỏ qua chứ không lỗi.
 *
 * Văn bản thuần: tên bé do khách gõ không thể chèn mã vào giao diện.
 */
public final class StaffNotificationFactory {

    public static final String DOCTOR_ASSIGNED = "DOCTOR_ASSIGNED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String NEW_REQUEST = "NEW_REQUEST";
    public static final String PAYMENT_PENDING = "PAYMENT_PENDING";
    public static final String NEW_ORDER = "NEW_ORDER";
    public static final String PROFILE_SETUP = "PROFILE_SETUP";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private StaffNotificationFactory() {
    }

    /** {@code type} là mã lưu vào cột type (frontend chọn biểu tượng theo đó). */
    public record Content(String type, String title, String message, String link) {
    }

    /** Báo bác sĩ: được xếp lịch mới, hoặc lịch của mình bị huỷ. Chỉ khi sự kiện nêu rõ bác sĩ. */
    public static Optional<Content> forDoctor(AppointmentNotificationEvent event) {
        if (event.doctorUserId() == null || event.type() == null) {
            return Optional.empty();
        }
        String pet = pet(event);
        String when = when(event.date(), event.startTime());
        return switch (event.type()) {
            case "CONFIRMED" -> Optional.of(new Content(DOCTOR_ASSIGNED,
                    "Bạn có lịch khám mới",
                    "Khám cho " + pet + at(when) + reason(event) + ".",
                    "/doctor/appointments"));
            case "CANCELLED_BY_CUSTOMER" -> Optional.of(new Content(APPOINTMENT_CANCELLED,
                    "Khách đã huỷ lịch khám",
                    "Lịch khám của " + pet + at(when) + " đã bị khách huỷ.",
                    "/doctor/appointments"));
            case "CANCELLED_BY_CLINIC" -> Optional.of(new Content(APPOINTMENT_CANCELLED,
                    "Lịch khám đã bị huỷ",
                    "Phòng khám đã huỷ lịch khám của " + pet + at(when) + ".",
                    "/doctor/appointments"));
            default -> Optional.empty();
        };
    }

    /** Báo admin: khách vừa gửi yêu cầu đặt lịch, cần xếp bác sĩ. */
    public static Optional<Content> forAdmin(AppointmentNotificationEvent event) {
        if (!"REQUEST_RECEIVED".equals(event.type())) {
            return Optional.empty();
        }
        return Optional.of(new Content(NEW_REQUEST,
                "Có yêu cầu đặt lịch mới",
                "Khách muốn khám cho " + pet(event) + at(when(event.date(), event.startTime())) + reason(event)
                        + ". Cần xếp bác sĩ.",
                "/admin/appointment-requests"));
    }

    /** Báo nhân viên quầy: khách vừa gửi yêu cầu đặt lịch (admin sẽ xếp bác sĩ, nhân viên theo dõi ở Lịch khám). */
    public static Optional<Content> forStaff(AppointmentNotificationEvent event) {
        if (!"REQUEST_RECEIVED".equals(event.type())) {
            return Optional.empty();
        }
        return Optional.of(new Content(NEW_REQUEST,
                "Có yêu cầu đặt lịch mới",
                "Khách muốn khám cho " + pet(event) + at(when(event.date(), event.startTime())) + reason(event) + ".",
                "/staff/appointments"));
    }

    /** Báo nhân viên quầy: có đơn hàng chờ xác nhận. Đơn online chỉ báo khi khách đã trả tiền. */
    public static Content newOrder(OrderPlacedEvent event) {
        boolean online = "ONLINE".equals(event.paymentMethod());
        String code = event.orderCode() == null || event.orderCode().isBlank() ? "mới" : event.orderCode().trim();
        String who = event.recipientName() == null || event.recipientName().isBlank()
                ? "" : " của " + event.recipientName().trim();
        return new Content(NEW_ORDER,
                "Có đơn hàng mới chờ xác nhận",
                "Đơn " + code + who + " gồm " + event.itemCount() + " sản phẩm"
                        + (event.total() == null ? "" : ", tổng " + money(event.total()))
                        + (online ? ", khách đã thanh toán online." : ", thanh toán khi nhận hàng."),
                "/staff/orders");
    }

    /** Báo nhân viên quầy: bác sĩ vừa lưu bệnh án nên có phiếu thu chờ chốt số tiền. */
    public static Content paymentPending(int drugCount) {
        return new Content(PAYMENT_PENDING,
                "Có phiếu thu mới chờ chốt số tiền",
                drugCount > 0
                        ? "Bác sĩ vừa kê " + drugCount + " loại thuốc. Hãy kiểm tra giá, số lượng và chốt tiền."
                        : "Bác sĩ vừa khám xong. Hãy kiểm tra tiền dịch vụ và chốt tiền.",
                "/staff/payments");
    }

    /**
     * Nhắc người vừa được admin tạo tài khoản tự cập nhật hồ sơ. Rỗng với vai trò không có trang hồ sơ
     * riêng (admin, khách).
     */
    public static Optional<Content> profileSetup(String role) {
        String link = role == null ? null : switch (role) {
            case "DOCTOR" -> "/doctor/profile";
            case "STAFF" -> "/staff/profile";
            default -> null;
        };
        if (link == null) {
            return Optional.empty();
        }
        return Optional.of(new Content(PROFILE_SETUP,
                "Hãy cập nhật hồ sơ của bạn",
                "Tài khoản của bạn vừa được phòng khám tạo. Vui lòng kiểm tra và bổ sung thông tin cá nhân "
                        + "trong trang Hồ sơ.",
                link));
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount.setScale(0, RoundingMode.HALF_UP))
                + "đ";
    }

    private static String pet(AppointmentNotificationEvent event) {
        return event.petName() == null || event.petName().isBlank() ? "một thú cưng" : event.petName().trim();
    }

    private static String reason(AppointmentNotificationEvent event) {
        return event.reason() == null || event.reason().isBlank() ? "" : " — " + event.reason().trim();
    }

    private static String when(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            return "";
        }
        return TIME_FORMAT.format(time) + " ngày " + DATE_FORMAT.format(date);
    }

    private static String at(String when) {
        return when.isEmpty() ? "" : " lúc " + when;
    }
}
