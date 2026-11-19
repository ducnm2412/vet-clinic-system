package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.service.StaffNotificationFactory;
import com.vetclinic.notification.service.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Báo phía phòng khám về lịch đặt, qua thông báo trong app (không email):
 * bác sĩ được xếp lịch hoặc lịch của mình bị huỷ, admin có yêu cầu đặt lịch mới cần xếp, và nhân viên quầy
 * có ca làm đúng ngày khách hẹn biết có yêu cầu đặt lịch mới (không ai có ca thì báo tất cả nhân viên).
 *
 * Nghe cùng routing key với thư báo khách nhưng hàng đợi riêng, nên hai việc không kéo nhau thử lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentStaffNoticeListener {

    private static final String ADMIN_ROLE = "ADMIN";
    
    private final NotificationService notificationService;
    private final UserDirectory userDirectory;

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_STAFF_NOTICE_QUEUE)
    public void onAppointmentNotification(AppointmentNotificationEvent event) {
        StaffNotificationFactory.forDoctor(event).ifPresent(c -> {
            notificationService.create(event.doctorUserId(), c.type(), c.title(), c.message(), c.link());
            log.info("Notified doctor {} about {} appointmentId={}", event.doctorUserId(), event.type(),
                    event.appointmentId());
        });

        StaffNotificationFactory.forAdmin(event).ifPresent(c -> {
            var admins = userDirectory.idsByRole(ADMIN_ROLE);
            notificationService.createForAll(admins, c.type(), c.title(), c.message(), c.link());
            log.info("Notified {} admin(s) about new request appointmentId={}", admins.size(), event.appointmentId());
        });

        StaffNotificationFactory.forStaff(event).ifPresent(c -> {
            var staff = userDirectory.staffOnShiftOrAll(event.date());
            notificationService.createForAll(staff, c.type(), c.title(), c.message(), c.link());
            log.info("Notified {} staff about new request appointmentId={}", staff.size(), event.appointmentId());
        });
    }
}
