package com.vetclinic.profile.messaging;

import com.vetclinic.profile.service.DoctorProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * VD-20: nhận họ tên bác sĩ từ auth-service. Tài khoản STAFF không cần tên ở trang công khai
 * nên bỏ qua — không tạo hồ sơ rỗng cho họ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffAccountCreatedListener {

    private final DoctorProfileService doctorProfileService;

    @RabbitListener(queues = RabbitMQConfig.STAFF_CREATED_QUEUE)
    public void onStaffAccountCreated(StaffAccountCreatedEvent event) {
        if (!"DOCTOR".equals(event.role())) {
            return;
        }
        doctorProfileService.ensureProfileWithName(event.userId(), event.fullName());
        log.info("Created doctor profile with name for userId={}", event.userId());
    }
}
