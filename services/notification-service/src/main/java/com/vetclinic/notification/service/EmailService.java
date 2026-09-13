package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentCreatedEvent;
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
