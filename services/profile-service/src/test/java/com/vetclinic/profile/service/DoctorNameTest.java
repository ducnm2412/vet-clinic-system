package com.vetclinic.profile.service;

import com.vetclinic.profile.dto.DoctorPublicResponse;
import com.vetclinic.profile.messaging.StaffAccountCreatedEvent;
import com.vetclinic.profile.messaging.StaffAccountCreatedListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** VD-20 — họ tên bác sĩ đi từ auth-service sang danh sách công khai. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@Transactional
class DoctorNameTest {

    @Autowired
    private StaffAccountCreatedListener listener;

    @Autowired
    private DoctorProfileService doctorProfileService;

    private DoctorPublicResponse publicEntry(UUID userId) {
        return doctorProfileService.listPublicDoctors().stream()
                .filter(d -> d.userId().equals(userId)).findFirst().orElse(null);
    }

    @Test
    void doctorAccountCreated_appearsPubliclyWithName() {
        // Trước đây bác sĩ phải tự mở trang hồ sơ một lần mới có mặt trong danh sách công khai.
        UUID userId = UUID.randomUUID();
        listener.onStaffAccountCreated(new StaffAccountCreatedEvent(userId, "bs@example.com", "Trần Minh Khoa", "DOCTOR"));

        assertThat(publicEntry(userId)).isNotNull();
        assertThat(publicEntry(userId).fullName()).isEqualTo("Trần Minh Khoa");
    }

    @Test
    void staffAccountCreated_doesNotCreateDoctorProfile() {
        UUID userId = UUID.randomUUID();
        listener.onStaffAccountCreated(new StaffAccountCreatedEvent(userId, "nv@example.com", "Lê Văn Tâm", "STAFF"));

        assertThat(publicEntry(userId)).isNull();
    }

    @Test
    void sameEventTwice_isSafe_andKeepsProfileDetails() {
        // RabbitMQ giao ít nhất một lần — nhận lại không được tạo trùng hay xoá chuyên môn đã khai.
        UUID userId = UUID.randomUUID();
        StaffAccountCreatedEvent event = new StaffAccountCreatedEvent(userId, "bs@example.com", "Phạm Thu Hà", "DOCTOR");
        listener.onStaffAccountCreated(event);
        doctorProfileService.updateMyProfile(userId,
                new com.vetclinic.profile.dto.DoctorProfileRequest("Da liễu", null, null, 5));
        listener.onStaffAccountCreated(event);

        assertThat(doctorProfileService.listPublicDoctors().stream().filter(d -> d.userId().equals(userId)))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.fullName()).isEqualTo("Phạm Thu Hà");
                    assertThat(d.specialty()).isEqualTo("Da liễu");
                });
    }
}
