package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.dto.CreateStaffAccountRequest;
import com.vetclinic.auth.messaging.StaffAccountCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** VD-20 — tạo tài khoản bác sĩ phải mang họ tên sang profile-service. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class StaffAccountCreatedEventTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private ApplicationEvents events;

    @Test
    void createDoctor_publishesFullNameInVietnameseOrder() {
        // Biểu mẫu đặt "Họ" vào firstName, "Tên" vào lastName.
        authService.createStaffAccount(new CreateStaffAccountRequest(
                "Trần Minh", "Khoa", "bs.event@example.com", "password123", RoleName.DOCTOR));

        StaffAccountCreatedEvent e = events.stream(StaffAccountCreatedEvent.class).findFirst().orElseThrow();
        assertThat(e.fullName()).isEqualTo("Trần Minh Khoa");
        assertThat(e.role()).isEqualTo("DOCTOR");
        assertThat(e.email()).isEqualTo("bs.event@example.com");
    }
}
