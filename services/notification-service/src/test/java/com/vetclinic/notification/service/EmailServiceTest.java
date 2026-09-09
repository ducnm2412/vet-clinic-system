package com.vetclinic.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit test (không @SpringBootTest) — notification-service không có DB, EmailService chỉ
// phụ thuộc JavaMailSender nên mock thẳng là đủ, không cần dựng Spring context.
class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(mailSender);

    @BeforeEach
    void setUp() {
        // @Value không chạy ngoài Spring context -> set thủ công giống giá trị mặc định trong
        // application.yml (app.frontend-url).
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
    }

    @Test
    void sendVerificationEmail_buildsCorrectSubjectLinkAndExpiryInHtmlBody() throws Exception {
        // JavaMailSender.createMimeMessage() phải trả về 1 MimeMessage thật (không mock được nội
        // dung dễ dàng) để MimeMessageHelper ghi header/body vào rồi đọc lại kiểm tra.
        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        Instant expiresAt = Instant.parse("2026-09-09T00:34:00Z"); // 07:34 ngày 09/09/2026 giờ VN

        emailService.sendVerificationEmail("jane@example.com", "Jane", "tok-abc123", expiresAt);

        verify(mailSender).send(mimeMessage);
        // saveChanges() bình thường do Transport.send() thật tự gọi trước khi ghi header —
        // ở đây mailSender.send() bị mock (không làm gì) nên phải tự gọi để đọc lại header cho đúng.
        mimeMessage.saveChanges();
        assertThat(mimeMessage.getSubject()).isEqualTo("Xác thực tài khoản VetClinic");
        assertThat(mimeMessage.getAllRecipients()[0].toString()).isEqualTo("jane@example.com");
        assertThat(mimeMessage.getContentType()).contains("text/html");

        String body = (String) mimeMessage.getContent();
        assertThat(body).contains("Chào Jane,");
        assertThat(body).contains("http://localhost:3000/verify-email?token=tok-abc123");
        assertThat(body).contains("07:34 ngày 09/09/2026");
    }

    @Test
    void sendVerificationEmail_usesConfiguredFrontendUrl_notHardcoded() throws Exception {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://vetclinic.example.com");
        MimeMessage mimeMessage = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendVerificationEmail("jane@example.com", "Jane", "tok-xyz",
                Instant.now().plus(1, ChronoUnit.DAYS));

        String body = (String) mimeMessage.getContent();
        assertThat(body).contains("https://vetclinic.example.com/verify-email?token=tok-xyz");
    }
}
