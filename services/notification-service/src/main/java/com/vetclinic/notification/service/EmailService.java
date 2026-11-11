package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentCreatedEvent;
import com.vetclinic.notification.messaging.AppointmentNotificationEvent;
import org.springframework.web.util.HtmlUtils;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    // Định dạng hạn dùng token theo giờ Việt Nam cho dễ đọc, không hiện raw Instant (UTC) trong email.
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;

    // Link trỏ về TRANG frontend (không phải thẳng API auth-service) — frontend tự gọi
    // GET /auth/verify-email?token= qua gateway rồi hiển thị kết quả cho người dùng.
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public void sendVerificationEmail(String toEmail, String firstName, String verificationToken,
                                       Instant verificationTokenExpiresAt) {
        String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;
        String expiresAtText = EXPIRY_FORMAT.format(verificationTokenExpiresAt);

        // HTML đơn giản, ghép chuỗi trực tiếp — chưa cần template engine (Thymeleaf/Freemarker...)
        // ở giai đoạn này, chỉ 1 loại email duy nhất.
        String html = "<p>Chào " + firstName + ",</p>"
                + "<p>Vui lòng xác thực tài khoản bằng cách nhấn vào link sau:</p>"
                + "<p><a href=\"" + verificationLink + "\">" + verificationLink + "</a></p>"
                + "<p>Link có hiệu lực đến <strong>" + expiresAtText + "</strong>. "
                + "Nếu hết hạn, vui lòng đăng ký lại.</p>"
                + "<p>VetClinic</p>";

        send(toEmail, "Xác thực tài khoản VetClinic", html);
    }

    /**
     * Quên mật khẩu. Tên người dùng do họ tự nhập lúc đăng ký nên được escape HTML. Link hết hạn
     * sau 1 giờ (auth-service quyết định) — hiện cả giờ hết hạn cụ thể thay vì chỉ nói "1 giờ" vì
     * người đọc có thể mở mail muộn.
     */
    public void sendPasswordResetEmail(String toEmail, String firstName, String resetToken,
                                       Instant resetTokenExpiresAt) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        String expiresAtText = EXPIRY_FORMAT.format(resetTokenExpiresAt);

        String html = "<p>Chào " + esc(firstName) + ",</p>"
                + "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản VetClinic của bạn. "
                + "Nhấn vào link sau để đặt mật khẩu mới:</p>"
                + "<p><a href=\"" + esc(resetLink) + "\">" + esc(resetLink) + "</a></p>"
                + "<p>Link có hiệu lực trong <strong>1 giờ</strong> (đến <strong>" + expiresAtText
                + "</strong>) và chỉ dùng được một lần.</p>"
                + "<p>Nếu bạn không yêu cầu việc này, hãy bỏ qua email — mật khẩu của bạn vẫn giữ nguyên.</p>"
                + "<p>VetClinic</p>";

        send(toEmail, "Đặt lại mật khẩu VetClinic", html);
    }

    /**
     * CN-43 — email xác nhận đặt lịch khám.
     *
     * Tên thú cưng và lý do khám do khách tự gõ, nên mọi giá trị đều được escape HTML trước khi
     * ghép vào. Không escape thì khách gõ thẻ HTML là chèn được nội dung tuỳ ý vào email mang tên
     * phòng khám.
     */
    public void sendAppointmentConfirmation(AppointmentCreatedEvent event) {
        String pet = event.petName() == null || event.petName().isBlank() ? "thú cưng của bạn" : event.petName();
        String doctor = event.doctorName() == null || event.doctorName().isBlank()
                ? "bác sĩ đang rảnh của phòng khám"
                : "bác sĩ " + event.doctorName();
        String link = frontendUrl + "/appointments/" + event.appointmentId();

        StringBuilder html = new StringBuilder()
                .append("<p>Phòng khám đã nhận lịch khám cho <strong>").append(esc(pet)).append("</strong>.</p>")
                .append("<p>Ngày <strong>").append(esc(DATE_FORMAT.format(event.date()))).append("</strong>, ")
                .append("từ <strong>").append(esc(TIME_FORMAT.format(event.startTime()))).append("</strong> ")
                .append("đến ").append(esc(TIME_FORMAT.format(event.endTime()))).append(".</p>")
                .append("<p>Người khám: ").append(esc(doctor)).append(".</p>");
        if (event.reason() != null && !event.reason().isBlank()) {
            html.append("<p>Lý do bạn đã ghi: ").append(esc(event.reason())).append("</p>");
        }
        html.append("<p>Xem chi tiết hoặc huỷ lịch: <a href=\"").append(esc(link)).append("\">")
                .append(esc(link)).append("</a></p>")
                .append("<p>VetClinic</p>");

        // Tiêu đề là văn bản thuần nên không cần escape HTML, nhưng phải bỏ ký tự xuống dòng:
        // tên thú cưng chứa CR/LF là chèn được thêm header vào email.
        send(event.customerEmail(), "Đã đặt lịch khám cho " + pet.replaceAll("[\r\n]+", " "), html.toString());
    }

    /**
     * Thư báo khách về lịch đặt theo yêu cầu. Trả {@code false} nếu loại thư không biết (booking-service
     * mới hơn bản này) — chỗ gọi bỏ qua chứ không coi là lỗi.
     *
     * Tên bé, lý do khám, tên dịch vụ do khách hoặc admin gõ nên mọi giá trị đều được escape.
     */
    public boolean sendAppointmentNotification(AppointmentNotificationEvent event) {
        String pet = event.petName() == null || event.petName().isBlank() ? "thú cưng của bạn" : event.petName();
        String doctor = event.doctorName() == null || event.doctorName().isBlank()
                ? "bác sĩ của phòng khám"
                : "bác sĩ " + event.doctorName();
        String link = frontendUrl + "/appointments/" + event.appointmentId();
        String type = event.type() == null ? "" : event.type();

        StringBuilder html = new StringBuilder();
        String subject;
        switch (type) {
            case "REQUEST_RECEIVED" -> {
                subject = "Đã nhận yêu cầu đặt lịch cho " + pet;
                html.append("<p>Phòng khám đã nhận yêu cầu đặt lịch khám cho <strong>").append(esc(pet))
                        .append("</strong>.</p>")
                        .append("<p>Giờ bạn mong muốn: ").append(when(event)).append("</p>");
                details(html, event);
                html.append("<p>Đây <strong>chưa phải lịch đã chốt</strong>. Phòng khám sẽ xếp bác sĩ rảnh vào "
                                + "giờ đó và báo lại cho bạn qua email này.</p>")
                        .append(linkLine("Xem trạng thái hoặc huỷ yêu cầu", link));
            }
            case "CONFIRMED" -> {
                subject = "Lịch khám của " + pet + " đã được xác nhận";
                html.append("<p>Lịch khám cho <strong>").append(esc(pet))
                        .append("</strong> đã được xác nhận.</p>")
                        .append("<p>").append(when(event)).append("</p>")
                        .append("<p>Người khám: ").append(esc(doctor)).append(".</p>");
                details(html, event);
                html.append(linkLine("Xem chi tiết hoặc huỷ lịch", link));
            }
            case "PROPOSAL" -> {
                subject = "Phòng khám đề xuất giờ khác cho " + pet;
                html.append("<p>Giờ bạn chọn");
                if (event.preferredDate() != null && event.preferredTime() != null) {
                    html.append(" (").append(esc(TIME_FORMAT.format(event.preferredTime()))).append(", ")
                            .append(esc(DATE_FORMAT.format(event.preferredDate()))).append(")");
                }
                html.append(" chưa xếp được bác sĩ cho <strong>").append(esc(pet)).append("</strong>.</p>")
                        .append("<p>Phòng khám đề xuất: ").append(when(event)).append(" Người khám: ")
                        .append(esc(doctor)).append(".</p>")
                        .append("<p><strong>Bạn cần trả lời:</strong> mở link bên dưới để đồng ý giờ này hoặc "
                                + "huỷ lịch. Phòng khám đang giữ chỗ này cho bạn.</p>")
                        .append(linkLine("Trả lời đề xuất", link));
            }
            case "CANCELLED_BY_CLINIC" -> {
                subject = "Lịch khám của " + pet + " đã bị huỷ";
                html.append("<p>Phòng khám đã huỷ lịch khám cho <strong>").append(esc(pet))
                        .append("</strong> (").append(when(event).replaceAll("\\.$", "")).append(").</p>")
                        .append("<p>Rất tiếc về sự bất tiện này. Bạn có thể gửi yêu cầu đặt lịch mới, hoặc gọi "
                                + "phòng khám để được hỗ trợ.</p>")
                        .append(linkLine("Đặt lịch mới", frontendUrl + "/appointments/create"));
            }
            default -> {
                return false;
            }
        }
        html.append("<p>VetClinic</p>");

        // Tiêu đề là văn bản thuần nên không escape HTML, nhưng phải bỏ ký tự xuống dòng: tên bé
        // chứa CR/LF là chèn được thêm header vào email.
        send(event.customerEmail(), subject.replaceAll("[\r\n]+", " "), html.toString());
        return true;
    }

    /** "Ngày 15/09/2026, từ 09:00 đến 09:30." — đã escape. */
    private static String when(AppointmentNotificationEvent event) {
        if (event.date() == null || event.startTime() == null) {
            return "";
        }
        String text = "Ngày <strong>" + esc(DATE_FORMAT.format(event.date())) + "</strong>, từ <strong>"
                + esc(TIME_FORMAT.format(event.startTime())) + "</strong>";
        if (event.endTime() != null) {
            text += " đến " + esc(TIME_FORMAT.format(event.endTime()));
        }
        return text + ".";
    }

    private static void details(StringBuilder html, AppointmentNotificationEvent event) {
        if (event.serviceName() != null && !event.serviceName().isBlank()) {
            html.append("<p>Dịch vụ: ").append(esc(event.serviceName())).append("</p>");
        }
        if (event.reason() != null && !event.reason().isBlank()) {
            html.append("<p>Lý do bạn đã ghi: ").append(esc(event.reason())).append("</p>");
        }
    }

    private static String linkLine(String label, String link) {
        return "<p>" + esc(label) + ": <a href=\"" + esc(link) + "\">" + esc(link) + "</a></p>";
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private void send(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent email \"{}\" to {}", subject, toEmail);
        } catch (MessagingException e) {
            // Ném lại unchecked để @RabbitListener thấy lỗi và requeue message (đúng hành vi
            // at-least-once) thay vì âm thầm mất email khi build message thất bại.
            throw new IllegalStateException("Failed to build email for " + toEmail, e);
        }
    }
}
