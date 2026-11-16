package com.vetclinic.profile.service;

import com.vetclinic.profile.dto.DoctorProfileRequest;
import com.vetclinic.profile.dto.StaffProfileRequest;
import com.vetclinic.profile.messaging.ProfileUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lưu hồ sơ phát sự kiện để notification-service gỡ thông báo "hãy cập nhật hồ sơ". */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class ProfileUpdatedEventTest {

    @Autowired private DoctorProfileService doctors;
    @Autowired private StaffProfileService staff;
    @Autowired private ApplicationEvents events;

    @Test
    void savingADoctorProfile_announcesIt() {
        UUID user = UUID.randomUUID();

        doctors.updateMyProfile(user, new DoctorProfileRequest("Ngoai khoa", "0911111111", "bio", 10));

        assertThat(events.stream(ProfileUpdatedEvent.class)).containsExactly(new ProfileUpdatedEvent(user, "DOCTOR"));
    }

    @Test
    void savingAStaffProfile_announcesIt() {
        UUID user = UUID.randomUUID();

        staff.updateMyProfile(user, new StaffProfileRequest("Le tan", "0922222222", null));

        assertThat(events.stream(ProfileUpdatedEvent.class)).containsExactly(new ProfileUpdatedEvent(user, "STAFF"));
    }

    @Test
    void justOpeningTheProfilePage_doesNotCountAsUpdatingIt() {
        UUID user = UUID.randomUUID();

        doctors.getMyProfile(user);
        staff.getMyProfile(UUID.randomUUID());

        assertThat(events.stream(ProfileUpdatedEvent.class)).isEmpty();
    }
}
