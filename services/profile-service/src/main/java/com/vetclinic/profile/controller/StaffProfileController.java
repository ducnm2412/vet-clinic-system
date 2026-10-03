package com.vetclinic.profile.controller;

import com.vetclinic.profile.dto.StaffProfileRequest;
import com.vetclinic.profile.dto.StaffProfileResponse;
import com.vetclinic.profile.security.jwt.AuthenticatedUser;
import com.vetclinic.profile.service.StaffProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class StaffProfileController {

    private final StaffProfileService staffProfileService;

    @GetMapping("/profile/staff/me")
    public StaffProfileResponse getMyProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return staffProfileService.getMyProfile(principal.userId());
    }

    @PutMapping("/profile/staff/me")
    public StaffProfileResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @Valid @RequestBody StaffProfileRequest request) {
        return staffProfileService.updateMyProfile(principal.userId(), request);
    }

    @PostMapping(value = "/profile/staff/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StaffProfileResponse uploadMyPhoto(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestParam("file") MultipartFile file) {
        return staffProfileService.uploadMyPhoto(principal.userId(), file);
    }

    @GetMapping("/profile/staff")
    public List<StaffProfileResponse> listAllStaff() {
        return staffProfileService.listAllStaff();
    }
}
