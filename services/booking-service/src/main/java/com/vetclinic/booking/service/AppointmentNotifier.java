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
        publish(Type.REQUEST_RECEIVED, a, null, null);
    }

    /** Đã có bác sĩ (admin xếp, hoặc khách đồng ý giờ đề xuất). Lịch phải đã gắn slot. */
    public void confirmed(Appointment a) {
        publish(Type.CONFIRMED, a, doctorOf(a), doctorIdOf(a));
    }

    /** Admin đề xuất giờ khác. Lịch phải đã gắn slot của bác sĩ được đề xuất. */
    public void proposal(Appointment a) {
        publish(Type.PROPOSAL, a, doctorOf(a), null);
    }

    /**
     * Phòng khám huỷ lịch của khách. Khách tự huỷ thì không gọi hàm này (họ vừa tự bấm).
     *
     * @param doctorHadIt lịch đã xếp bác sĩ trước khi huỷ — bác sĩ cần được báo lịch của mình biến mất
     */
    public void cancelledByClinic(Appointment a, boolean doctorHadIt) {
        publish(Type.CANCELLED_BY_CLINIC, a, null, doctorHadIt ? doctorIdOf(a) : null);
    }

    /**
     * Phòng khám huỷ yêu cầu chờ xếp bác sĩ vì đã quá giờ khách chọn. Chỉ báo khách: lịch chưa có
     * bác sĩ nên không ai khác cần biết.
     */
    public void cancelledOverdue(Appointment a) {
        publish(Type.CANCELLED_OVERDUE, a, null, null);
    }

    /** Khách tự huỷ lịch đã xếp bác sĩ: chỉ bác sĩ cần biết. */
    public void cancelledByCustomer(Appointment a) {
        publish(Type.CANCELLED_BY_CUSTOMER, a, null, doctorIdOf(a));
    }

    private void publish(Type type, Appointment a, String doctorName, UUID doctorUserId) {
        events.publishEvent(new AppointmentNotificationEvent(type, a.getId(), a.getCustomerUserId(),
                a.getCustomerEmail(), a.getPetName(),
                a.getService() == null ? null : a.getService().getName(), a.getReason(), doctorName,
                a.effectiveDate(), a.effectiveStartTime(), a.effectiveEndTime(),
                a.getPreferredDate(), a.getPreferredTime(), doctorUserId));
    }

    private UUID doctorIdOf(Appointment a) {
        return a.getSlot() == null ? null : a.getSlot().getDoctorUserId();
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
