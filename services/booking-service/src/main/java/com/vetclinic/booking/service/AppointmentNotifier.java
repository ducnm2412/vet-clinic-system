package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.messaging.AppointmentNotificationEvent;
import com.vetclinic.booking.messaging.AppointmentNotificationEvent.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Báo khách về những bước của lịch đặt theo yêu cầu (email và thông báo trong app).
 *
 * Sự kiện chỉ thật sự gửi đi sau khi transaction commit ({@code AppointmentEventPublisher}), nên
 * thao tác bị rollback không bao giờ gửi thông báo. Lịch không có email (đặt kiểu cũ, tạo hộ tại
 * quầy) vẫn phát sự kiện — thông báo trong app không cần email; notification-service tự bỏ qua
 * phần gửi thư.
 */
@Component
@RequiredArgsConstructor
public class AppointmentNotifier {

    private final ApplicationEventPublisher events;
    private final DoctorDirectory doctorDirectory;

    /** Khách vừa gửi yêu cầu. Giờ trong thư là giờ khách mong muốn. */
    public void requestReceived(Appointment a) {
        publish(Type.REQUEST_RECEIVED, a, null);
    }

    /** Đã có bác sĩ (admin xếp, hoặc khách đồng ý giờ đề xuất). Lịch phải đã gắn slot. */
    public void confirmed(Appointment a) {
        publish(Type.CONFIRMED, a, doctorOf(a));
    }

    /** Admin đề xuất giờ khác. Lịch phải đã gắn slot của bác sĩ được đề xuất. */
    public void proposal(Appointment a) {
        publish(Type.PROPOSAL, a, doctorOf(a));
    }

    /** Phòng khám huỷ lịch của khách. Khách tự huỷ thì không gọi hàm này (họ vừa tự bấm). */
    public void cancelledByClinic(Appointment a) {
        publish(Type.CANCELLED_BY_CLINIC, a, null);
    }

    private void publish(Type type, Appointment a, String doctorName) {
        events.publishEvent(new AppointmentNotificationEvent(type, a.getId(), a.getCustomerUserId(),
                a.getCustomerEmail(), a.getPetName(),
                a.getService() == null ? null : a.getService().getName(), a.getReason(), doctorName,
                a.effectiveDate(), a.effectiveStartTime(), a.effectiveEndTime(),
                a.getPreferredDate(), a.getPreferredTime()));
    }

    /** Không tra được tên thì để trống chứ không làm hỏng việc xếp lịch — thư tự bỏ phần đó. */
    private String doctorOf(Appointment a) {
        AppointmentSlot slot = a.getSlot();
        if (slot == null) {
            return null;
        }
        UUID id = slot.getDoctorUserId();
        try {
            return doctorDirectory.activeDoctors().stream()
                    .filter(d -> id.equals(d.userId()))
                    .map(d -> d.fullName())
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
