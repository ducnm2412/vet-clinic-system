package com.vetclinic.profile.service;

import com.vetclinic.profile.dto.StaffProfileRequest;
import com.vetclinic.profile.dto.StaffProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class StaffProfileServiceTest {

    @Autowired
    private StaffProfileService staffProfileService;

    @Test
    void getMyProfile_lazyCreatesProfile() {
        UUID userId = UUID.randomUUID();

        StaffProfileResponse response = staffProfileService.getMyProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void updateMyProfile_updatesFields() {
        UUID userId = UUID.randomUUID();

        StaffProfileResponse response = staffProfileService.updateMyProfile(userId,
                new StaffProfileRequest("Le tan", "0922222222", LocalDate.of(2023, 3, 1)));

        assertThat(response.position()).isEqualTo("Le tan");
        assertThat(response.hireDate()).isEqualTo(LocalDate.of(2023, 3, 1));
    }

    @Test
    void listAllStaff_includesCreatedProfile() {
        UUID userId = UUID.randomUUID();
        staffProfileService.updateMyProfile(userId, new StaffProfileRequest("Ky thuat vien", null, null));

        List<StaffProfileResponse> all = staffProfileService.listAllStaff();

        assertThat(all).anyMatch(s -> s.userId().equals(userId) && s.position().equals("Ky thuat vien"));
    }
}
