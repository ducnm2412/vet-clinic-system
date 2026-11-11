package com.vetclinic.notification.service;

import com.vetclinic.notification.messaging.AppointmentNotificationEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** Bốn loại thư báo khách về lịch đặt theo yêu cầu. */
class AppointmentNotificationEmailTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(mailSender);
    private final UUID appointmentId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:3000");
    }

    private AppointmentNotificationEvent event(String type, String pet, String doctor, String reason, String service) {
        return new AppointmentNotificationEvent(type, appointmentId, "khach@example.com", pet, service, reason,
                doctor, LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), LocalTime.of(10, 30),
                LocalDate.of(2026, 12, 1), LocalTime.of(9, 0));
    }

    private String send(AppointmentNotificationEvent event) throws Exception {
        MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        assertThat(emailService.sendAppointmentNotification(event)).isTrue();
        verify(mailSender).send(mime);
        mime.saveChanges();
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("khach@example.com");
        return mime.getSubject() + "\n" + mime.getContent();
    }

    @Test
    void requestReceived_saysItIsNotConfirmedYet() throws Exception {
        String mail = send(event("REQUEST_RECEIVED", "Milo", null, "Ngứa tai", "Tiêm phòng"));

        assertThat(mail).contains("Đã nhận yêu cầu đặt lịch cho Milo");
        assertThat(mail).contains("01/12/2026").contains("10:00").contains("10:30");
        assertThat(mail).contains("chưa phải lịch đã chốt");
        assertThat(mail).contains("Ngứa tai").contains("Tiêm phòng");
        assertThat(mail).contains("http://localhost:3000/appointments/" + appointmentId);
    }

    @Test
    void confirmed_namesTheDoctor() throws Exception {
        String mail = send(event("CONFIRMED", "Milo", "Trần Minh Khoa", null, null));

        assertThat(mail).contains("Lịch khám của Milo đã được xác nhận");
        assertThat(mail).contains("bác sĩ Trần Minh Khoa").contains("10:00");
        assertThat(mail).contains("http://localhost:3000/appointments/" + appointmentId);
        assertThat(mail).doesNotContain("Lý do bạn đã ghi").doesNotContain("Dịch vụ:");
    }

    @Test
    void proposal_showsWhatTheCustomerAskedForAndWhatIsProposed_andAsksForAnAnswer() throws Exception {
        String mail = send(event("PROPOSAL", "Milo", "Trần Minh Khoa", null, null));

        assertThat(mail).contains("Phòng khám đề xuất giờ khác cho Milo");
        // Giờ khách chọn (9:00) và giờ đề xuất (10:00) đều có mặt.
        assertThat(mail).contains("09:00").contains("10:00").contains("bác sĩ Trần Minh Khoa");
        assertThat(mail).contains("Bạn cần trả lời");
        assertThat(mail).contains("http://localhost:3000/appointments/" + appointmentId);
    }

    @Test
    void cancelledByClinic_pointsToANewRequest() throws Exception {
        String mail = send(event("CANCELLED_BY_CLINIC", "Milo", null, null, null));

        assertThat(mail).contains("Lịch khám của Milo đã bị huỷ");
        assertThat(mail).contains("http://localhost:3000/appointments/create");
        // Dấu chấm nằm sau ngoặc, không kẹt trong ngoặc.
        assertThat(mail).contains("đến 10:30).").doesNotContain("10:30.)");
    }

    @Test
    void escapesHtmlTypedByCustomer() throws Exception {
        String mail = send(event("REQUEST_RECEIVED", "<b>Milo</b>", null,
                "<a href=\"http://lua.dao\">bấm vào đây</a>", "<script>x</script>"));
        String body = mail.substring(mail.indexOf('\n') + 1);

        assertThat(body).doesNotContain("<b>Milo</b>").contains("&lt;b&gt;Milo&lt;/b&gt;");
        assertThat(body).doesNotContain("<a href=\"http://lua.dao\">").doesNotContain("<script>");
    }

    @Test
    void stripsLineBreaksFromSubject() throws Exception {
        String mail = send(event("CONFIRMED", "Milo\r\nBcc: ai-do@example.com", null, null, null));
        String subject = mail.substring(0, mail.indexOf('\n'));

        assertThat(subject).doesNotContain("\r").isEqualTo("Lịch khám của Milo Bcc: ai-do@example.com đã được xác nhận");
    }

    @Test
    void fallsBackWhenNamesMissing() throws Exception {
        String mail = send(event("CONFIRMED", null, null, null, null));

        assertThat(mail).doesNotContain("null");
        assertThat(mail).contains("thú cưng của bạn").contains("bác sĩ của phòng khám");
    }

    @Test
    void unknownType_isSkippedNotFailed() {
        assertThat(emailService.sendAppointmentNotification(event("SOMETHING_NEW", "Milo", null, null, null)))
                .isFalse();
        assertThat(emailService.sendAppointmentNotification(event(null, "Milo", null, null, null))).isFalse();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
