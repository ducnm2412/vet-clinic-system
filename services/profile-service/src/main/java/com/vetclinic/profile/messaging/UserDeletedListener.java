package com.vetclinic.profile.messaging;

import com.vetclinic.profile.service.CustomerProfileService;
import com.vetclinic.profile.service.DoctorProfileService;
import com.vetclinic.profile.service.StaffProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedListener {

    private final CustomerProfileService customerProfileService;
    private final DoctorProfileService doctorProfileService;
    private final StaffProfileService staffProfileService;

    // Không biết user vừa xoá từng có role gì — thử xoá cả 3 loại profile, cái nào không có thì no-op.
    @RabbitListener(queues = RabbitMQConfig.USER_DELETED_QUEUE)
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted event for userId={}", event.userId());
        customerProfileService.deleteByUserId(event.userId());
        doctorProfileService.deleteByUserId(event.userId());
        staffProfileService.deleteByUserId(event.userId());
    }
}
