package com.vetclinic.booking.messaging;

import com.vetclinic.booking.service.DoctorBlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** CN-08: chỉ quan tâm tài khoản bác sĩ — khoá khách hay nhân viên không ảnh hưởng giờ khám. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusChangedListener {

    private final DoctorBlockService doctorBlockService;

    @RabbitListener(queues = RabbitMQConfig.USER_STATUS_QUEUE)
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        if (event.roles() == null || !event.roles().contains("DOCTOR")) {
            return;
        }
        log.info("Received user.{} for doctor {}", event.locked() ? "locked" : "unlocked", event.userId());
        if (event.locked()) {
            doctorBlockService.block(event.userId());
        } else {
            doctorBlockService.unblock(event.userId());
        }
    }
}
