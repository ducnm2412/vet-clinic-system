package com.vetclinic.profile.service;

import com.vetclinic.profile.dto.DoctorLicenseRequest;
import com.vetclinic.profile.dto.DoctorLicenseResponse;
import com.vetclinic.profile.dto.DoctorProfileRequest;
import com.vetclinic.profile.dto.DoctorProfileResponse;
import com.vetclinic.profile.dto.DoctorPublicResponse;
import com.vetclinic.profile.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class DoctorProfileServiceTest {

    @Autowired
    private DoctorProfileService doctorProfileService;

    @Test
    void getMyProfile_lazyCreatesProfile() {
        UUID userId = UUID.randomUUID();

        DoctorProfileResponse response = doctorProfileService.getMyProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void updateMyProfile_updatesFields() {
        UUID userId = UUID.randomUUID();

        DoctorProfileResponse response = doctorProfileService.updateMyProfile(userId,
                new DoctorProfileRequest("Ngoai khoa", "0911111111", "10 nam kinh nghiem", 10));

        assertThat(response.specialty()).isEqualTo("Ngoai khoa");
        assertThat(response.yearsOfExperience()).isEqualTo(10);
    }

    @Test
    void createLicense_thenUpdateThenDelete() {
        UUID userId = UUID.randomUUID();

        DoctorLicenseResponse created = doctorProfileService.createLicense(userId,
                new DoctorLicenseRequest("VN-12345", "Bo Y Te", LocalDate.of(2015, 1, 1), null));
        assertThat(doctorProfileService.listLicenses(userId)).hasSize(1);

        DoctorLicenseResponse updated = doctorProfileService.updateLicense(userId, created.id(),
                new DoctorLicenseRequest("VN-99999", "Bo Y Te", LocalDate.of(2015, 1, 1), LocalDate.of(2030, 1, 1)));
        assertThat(updated.licenseNumber()).isEqualTo("VN-99999");
        assertThat(updated.expiryDate()).isEqualTo(LocalDate.of(2030, 1, 1));

        doctorProfileService.deleteLicense(userId, created.id());
        assertThat(doctorProfileService.listLicenses(userId)).isEmpty();
    }

    @Test
    void updateLicense_wrongOwner_throws() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        DoctorLicenseResponse license = doctorProfileService.createLicense(owner,
                new DoctorLicenseRequest("VN-12345", "Bo Y Te", LocalDate.of(2015, 1, 1), null));

        assertThatThrownBy(() -> doctorProfileService.updateLicense(intruder, license.id(),
                new DoctorLicenseRequest("HACKED", "X", LocalDate.of(2020, 1, 1), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPublicDoctors_includesUpdatedProfile() {
        UUID userId = UUID.randomUUID();
        doctorProfileService.updateMyProfile(userId,
                new DoctorProfileRequest("Da lieu", "0900000000", "bio", 5));

        List<DoctorPublicResponse> doctors = doctorProfileService.listPublicDoctors();

        assertThat(doctors)
                .anyMatch(d -> d.userId().equals(userId) && d.specialty().equals("Da lieu"));
    }
}
