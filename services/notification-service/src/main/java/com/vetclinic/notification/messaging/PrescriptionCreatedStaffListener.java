package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.service.StaffNotificationFactory;
import com.vetclinic.notification.service.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Bác sĩ vừa lưu bệnh án lần đầu nên payment-service có phiếu thu chờ chốt số tiền: báo mọi nhân
 * viên quầy. Phiếu thu chưa có sự kiện riêng; sự kiện kê đơn là thời điểm phiếu được tạo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionCreatedStaffListener {

    private static final String STAFF_ROLE = "STAFF";

    private final NotificationService notificationService;
    private final UserDirectory userDirectory;

    @RabbitListener(queues = RabbitMQConfig.PRESCRIPTION_CREATED_QUEUE)
    public void onPrescriptionCreated(PrescriptionCreatedEvent event) {
        var content = StaffNotificationFactory.paymentPending(event.drugCount());
        var staff = userDirectory.idsByRole(STAFF_ROLE);
        notificationService.createForAll(staff, content.type(), content.title(), content.message(), content.link());
        log.info("Notified {} staff about pending payment, medicalRecordId={}", staff.size(), event.medicalRecordId());
    }
}
