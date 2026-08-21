package com.vetclinic.profile.service;

import com.vetclinic.profile.domain.DoctorLicense;
import com.vetclinic.profile.domain.DoctorProfile;
import com.vetclinic.profile.dto.DoctorLicenseRequest;
import com.vetclinic.profile.dto.DoctorLicenseResponse;
import com.vetclinic.profile.dto.DoctorProfileRequest;
import com.vetclinic.profile.dto.DoctorProfileResponse;
import com.vetclinic.profile.dto.DoctorPublicResponse;
import com.vetclinic.profile.exception.ResourceNotFoundException;
import com.vetclinic.profile.repository.DoctorLicenseRepository;
import com.vetclinic.profile.repository.DoctorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorProfileService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final DoctorLicenseRepository doctorLicenseRepository;

    @Transactional
    public DoctorProfileResponse getMyProfile(UUID userId) {
        return toProfileResponse(getOrCreateProfile(userId));
    }

    @Transactional
    public DoctorProfileResponse updateMyProfile(UUID userId, DoctorProfileRequest request) {
        DoctorProfile profile = getOrCreateProfile(userId);
        profile.setSpecialty(request.specialty());
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        profile.setYearsOfExperience(request.yearsOfExperience());

        doctorProfileRepository.saveAndFlush(profile);
        return toProfileResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<DoctorLicenseResponse> listLicenses(UUID userId) {
        DoctorProfile profile = getOrCreateProfile(userId);
        return doctorLicenseRepository.findByDoctorProfileId(profile.getId()).stream()
                .map(this::toLicenseResponse)
                .toList();
    }

    @Transactional
    public DoctorLicenseResponse createLicense(UUID userId, DoctorLicenseRequest request) {
        DoctorProfile profile = getOrCreateProfile(userId);

        DoctorLicense license = DoctorLicense.builder()
                .doctorProfile(profile)
                .licenseNumber(request.licenseNumber())
                .issuedBy(request.issuedBy())
                .issuedDate(request.issuedDate())
                .expiryDate(request.expiryDate())
                .build();

        doctorLicenseRepository.saveAndFlush(license);
        return toLicenseResponse(license);
    }

    @Transactional
    public DoctorLicenseResponse updateLicense(UUID userId, UUID licenseId, DoctorLicenseRequest request) {
        DoctorProfile profile = getOrCreateProfile(userId);
        DoctorLicense license = doctorLicenseRepository.findByIdAndDoctorProfileId(licenseId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("License not found: " + licenseId));

        license.setLicenseNumber(request.licenseNumber());
        license.setIssuedBy(request.issuedBy());
        license.setIssuedDate(request.issuedDate());
        license.setExpiryDate(request.expiryDate());

        doctorLicenseRepository.saveAndFlush(license);
        return toLicenseResponse(license);
    }

    @Transactional
    public void deleteLicense(UUID userId, UUID licenseId) {
        DoctorProfile profile = getOrCreateProfile(userId);
        DoctorLicense license = doctorLicenseRepository.findByIdAndDoctorProfileId(licenseId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("License not found: " + licenseId));
        doctorLicenseRepository.delete(license);
    }

    @Transactional(readOnly = true)
    public List<DoctorPublicResponse> listPublicDoctors() {
        return doctorProfileRepository.findAll().stream()
                .map(p -> new DoctorPublicResponse(p.getId(), p.getUserId(), p.getSpecialty(), p.getBio(),
                        p.getYearsOfExperience()))
                .toList();
    }

    private DoctorProfile getOrCreateProfile(UUID userId) {
        return doctorProfileRepository.findByUserId(userId)
                .orElseGet(() -> doctorProfileRepository.saveAndFlush(
                        DoctorProfile.builder().userId(userId).specialty("").build()));
    }

    private DoctorProfileResponse toProfileResponse(DoctorProfile profile) {
        return new DoctorProfileResponse(profile.getId(), profile.getUserId(), profile.getSpecialty(),
                profile.getPhone(), profile.getBio(), profile.getYearsOfExperience(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }

    private DoctorLicenseResponse toLicenseResponse(DoctorLicense license) {
        return new DoctorLicenseResponse(license.getId(), license.getLicenseNumber(), license.getIssuedBy(),
                license.getIssuedDate(), license.getExpiryDate(), license.getCreatedAt(), license.getUpdatedAt());
    }
}
