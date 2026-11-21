package com.vetclinic.profile.service;

import com.vetclinic.profile.domain.CustomerProfile;
import com.vetclinic.profile.domain.DoctorProfile;
import com.vetclinic.profile.domain.StaffProfile;
import com.vetclinic.profile.dto.PhotoUrls;
import com.vetclinic.profile.repository.CustomerProfileRepository;
import com.vetclinic.profile.repository.DoctorProfileRepository;
import com.vetclinic.profile.repository.StaffProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Chuyển ảnh đại diện cũ trên đĩa lên Cloudinary, chạy lúc khởi động khi Cloudinary đã được cấu hình. Chỉ đụng hồ
 * sơ còn ảnh trên đĩa mà chưa có URL, nên chạy lại bao nhiêu lần cũng không làm gì thêm. Lỗi một ảnh chỉ ghi log
 * rồi để lần khởi động sau thử lại — không làm service hỏng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyPhotoMigration implements ApplicationRunner {

    private final PhotoStorageService photos;
    private final CloudinaryUploader cloudinary;
    private final DoctorProfileRepository doctors;
    private final StaffProfileRepository staff;
    private final CustomerProfileRepository customers;

    @Override
    public void run(ApplicationArguments args) {
        if (!cloudinary.enabled()) {
            return;
        }
        int moved = migrate(PhotoUrls.DOCTORS, doctors.findByPhotoVersionGreaterThanAndPhotoUrlIsNull(0),
                DoctorProfile::getId, DoctorProfile::setPhotoUrl, doctors::save)
                + migrate(PhotoUrls.STAFF, staff.findByPhotoVersionGreaterThanAndPhotoUrlIsNull(0),
                StaffProfile::getId, StaffProfile::setPhotoUrl, staff::save)
                + migrate(PhotoUrls.CUSTOMERS, customers.findByPhotoVersionGreaterThanAndPhotoUrlIsNull(0),
                CustomerProfile::getId, CustomerProfile::setPhotoUrl, customers::save);
        if (moved > 0) {
            log.info("Đã chuyển {} ảnh đại diện cũ lên Cloudinary", moved);
        }
    }

    private <T> int migrate(String kind, List<T> profiles, Function<T, UUID> idOf, BiConsumer<T, String> setUrl,
                            Function<T, T> save) {
        int moved = 0;
        for (T profile : profiles) {
            try {
                String url = photos.uploadLegacyFile(kind, idOf.apply(profile));
                if (url != null) {
                    setUrl.accept(profile, url);
                    save.apply(profile);
                    moved++;
                }
            } catch (RuntimeException e) {
                log.warn("Chưa chuyển được ảnh {} {} lên Cloudinary, thử lại lần khởi động sau: {}", kind,
                        idOf.apply(profile), e.getMessage());
            }
        }
        return moved;
    }
}
