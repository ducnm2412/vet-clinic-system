package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentCreatedEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CN-43 — email xác nhận đặt lịch khám. */
class AppointmentConfirmationEmailTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(mailSender);
    private final UUID appointmentId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
    }

    private AppointmentCreatedEvent event(String petName, String doctorName, String reason) {
        return new AppointmentCreatedEvent(appointmentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.of(2026, 9, 15), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "khach@example.com", petName, doctorName, reason);
    }

    private String send(AppointmentCreatedEvent event) throws Exception {
        MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        emailService.sendAppointmentConfirmation(event);
        verify(mailSender).send(mime);
        mime.saveChanges();
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("khach@example.com");
        return mime.getSubject() + "\n" + mime.getContent();
    }

    @Test
    void containsPetDateTimeDoctorAndLink() throws Exception {
        String mail = send(event("Milo", "Trần Minh Khoa", "Bỏ ăn hai ngày"));

        assertThat(mail).contains("Đã đặt lịch khám cho Milo");
        assertThat(mail).contains("15/09/2026").contains("09:00").contains("09:30");
        assertThat(mail).contains("bác sĩ Trần Minh Khoa");
        assertThat(mail).contains("Bỏ ăn hai ngày");
        assertThat(mail).contains("http://localhost:3000/appointments/" + appointmentId);
    }

    @Test
    void escapesHtmlTypedByCustomer() throws Exception {
        // Tên thú cưng và lý do khám do khách tự gõ. Không escape thì chèn được HTML tuỳ ý
        // vào một email mang tên phòng khám — ví dụ một đường link lừa đăng nhập.
        String mail = send(event("<b>Milo</b>", null, "<a href=\"http://lua.dao\">bấm vào đây</a>"));
        String body = mail.substring(mail.indexOf('\n') + 1);

        // Thân email là HTML: phải escape.
        assertThat(body).doesNotContain("<b>Milo</b>").contains("&lt;b&gt;Milo&lt;/b&gt;");
        assertThat(body).doesNotContain("<a href=\"http://lua.dao\">");
    }

    @Test
    void stripsLineBreaksFromSubject() throws Exception {
        // Tiêu đề là header thuần văn bản — nguy cơ ở đây là xuống dòng để chèn header, không phải HTML.
        String mail = send(event("Milo\r\nBcc: ai-do@example.com", null, null));
        String subject = mail.substring(0, mail.indexOf('\n'));

        assertThat(subject).doesNotContain("\r")
                .isEqualTo("Đã đặt lịch khám cho Milo Bcc: ai-do@example.com");
    }

    @Test
    void fallsBackWhenNamesMissing() throws Exception {
        // booking-service không tra được tên thì vẫn gửi, chỉ bớt chi tiết — không in ra "null".
        String mail = send(event(null, null, null));

        assertThat(mail).doesNotContain("null");
        assertThat(mail).contains("thú cưng của bạn").contains("bác sĩ đang rảnh của phòng khám");
        assertThat(mail).doesNotContain("Lý do bạn đã ghi");
    }
}
