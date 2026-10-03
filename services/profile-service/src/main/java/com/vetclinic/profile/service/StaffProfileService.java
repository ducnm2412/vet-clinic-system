package com.vetclinic.profile.service;

import com.vetclinic.profile.domain.StaffProfile;
import com.vetclinic.profile.dto.PhotoUrls;
import com.vetclinic.profile.dto.StaffProfileRequest;
import com.vetclinic.profile.dto.StaffProfileResponse;
import com.vetclinic.profile.repository.StaffProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffProfileService {

    private final StaffProfileRepository staffProfileRepository;
    private final PhotoStorageService photoStorageService;

    @Transactional
    public StaffProfileResponse uploadMyPhoto(UUID userId, MultipartFile file) {
        StaffProfile profile = getOrCreateProfile(userId);
        photoStorageService.store(PhotoUrls.STAFF, profile.getId(), file);
        profile.setPhotoVersion(profile.getPhotoVersion() + 1);
        staffProfileRepository.saveAndFlush(profile);
        return toResponse(profile);
    }

    @Transactional
    public StaffProfileResponse getMyProfile(UUID userId) {
        return toResponse(getOrCreateProfile(userId));
    }

    // Gọi từ UserDeletedListener khi auth-service báo user đã bị xoá — dọn dữ liệu mồ côi.
    @Transactional
    public void deleteByUserId(UUID userId) {
        staffProfileRepository.findByUserId(userId).ifPresent(staffProfileRepository::delete);
    }

    @Transactional
    public StaffProfileResponse updateMyProfile(UUID userId, StaffProfileRequest request) {
        StaffProfile profile = getOrCreateProfile(userId);
        profile.setPosition(request.position());
        profile.setPhone(request.phone());
        profile.setHireDate(request.hireDate());

        staffProfileRepository.saveAndFlush(profile);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<StaffProfileResponse> listAllStaff() {
        return staffProfileRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private StaffProfile getOrCreateProfile(UUID userId) {
        return staffProfileRepository.findByUserId(userId)
                .orElseGet(() -> staffProfileRepository.saveAndFlush(
                        StaffProfile.builder().userId(userId).position("").build()));
    }

    private StaffProfileResponse toResponse(StaffProfile profile) {
        return new StaffProfileResponse(profile.getId(), profile.getUserId(), profile.getPosition(),
                profile.getPhone(), profile.getHireDate(),
                PhotoUrls.of(PhotoUrls.STAFF, profile.getId(), profile.getPhotoVersion()),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
