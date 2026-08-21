package com.vetclinic.profile.controller;

import com.vetclinic.profile.dto.DoctorLicenseRequest;
import com.vetclinic.profile.dto.DoctorLicenseResponse;
import com.vetclinic.profile.dto.DoctorProfileRequest;
import com.vetclinic.profile.dto.DoctorProfileResponse;
import com.vetclinic.profile.dto.DoctorPublicResponse;
import com.vetclinic.profile.security.jwt.AuthenticatedUser;
import com.vetclinic.profile.service.DoctorProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DoctorProfileController {

    private final DoctorProfileService doctorProfileService;

    @GetMapping("/profile/doctor/me")
    public DoctorProfileResponse getMyProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return doctorProfileService.getMyProfile(principal.userId());
    }

    @PutMapping("/profile/doctor/me")
    public DoctorProfileResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @Valid @RequestBody DoctorProfileRequest request) {
        return doctorProfileService.updateMyProfile(principal.userId(), request);
    }

    @GetMapping("/profile/doctor/me/licenses")
    public List<DoctorLicenseResponse> listLicenses(@AuthenticationPrincipal AuthenticatedUser principal) {
        return doctorProfileService.listLicenses(principal.userId());
    }

    @PostMapping("/profile/doctor/me/licenses")
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorLicenseResponse createLicense(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody DoctorLicenseRequest request) {
        return doctorProfileService.createLicense(principal.userId(), request);
    }

    @PutMapping("/profile/doctor/me/licenses/{licenseId}")
    public DoctorLicenseResponse updateLicense(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID licenseId,
                                                @Valid @RequestBody DoctorLicenseRequest request) {
        return doctorProfileService.updateLicense(principal.userId(), licenseId, request);
    }

    @DeleteMapping("/profile/doctor/me/licenses/{licenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLicense(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID licenseId) {
        doctorProfileService.deleteLicense(principal.userId(), licenseId);
    }

    @GetMapping("/profile/doctors")
    public List<DoctorPublicResponse> listPublicDoctors() {
        return doctorProfileService.listPublicDoctors();
    }
}
