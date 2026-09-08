package com.vetclinic.notification.service;

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

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Xác thực tài khoản VetClinic");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent verification email to {}", toEmail);
        } catch (MessagingException e) {
            // Ném lại unchecked để @RabbitListener thấy lỗi và requeue message (đúng hành vi
            // at-least-once) thay vì âm thầm mất email khi build message thất bại.
            throw new IllegalStateException("Failed to build verification email for " + toEmail, e);
        }
    }
}
