package com.vetclinic.profile.controller;

import com.vetclinic.profile.dto.PhotoUrls;
import com.vetclinic.profile.service.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

// Đọc ảnh đại diện cho cả 4 loại. Bác sĩ và nhân viên mở công khai, thú cưng và khách hàng cần
// đăng nhập — phân quyền theo path nằm ở SecurityConfig, không phải ở đây.
@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoStorageService photoStorageService;

    @GetMapping("/profile/photos/{kind}/{id}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String kind, @PathVariable UUID id) {
        PhotoStorageService.StoredPhoto photo = photoStorageService.load(kind, id);

        // URL luôn kèm ?v=<photoVersion> nên đổi ảnh là đổi URL — cache lâu được.
        boolean isPublic = PhotoUrls.DOCTORS.equals(kind) || PhotoUrls.STAFF.equals(kind);
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(30));
        cache = isPublic ? cache.cachePublic() : cache.cachePrivate();

        return ResponseEntity.ok()
                .contentType(photo.mediaType())
                .cacheControl(cache)
                .body(photo.resource());
    }
}
