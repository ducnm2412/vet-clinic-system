package com.vetclinic.pet.service;

import com.vetclinic.pet.domain.Pet;
import com.vetclinic.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Chuyển ảnh thú cưng cũ trên đĩa lên Cloudinary, chạy lúc khởi động khi Cloudinary đã được cấu hình. Chỉ đụng
 * con còn ảnh trên đĩa mà chưa có URL, nên chạy lại bao nhiêu lần cũng không làm gì thêm. Lỗi một ảnh chỉ ghi log
 * rồi để lần khởi động sau thử lại — không làm service hỏng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyPhotoMigration implements ApplicationRunner {

    private final PetPhotoStorageService photos;
    private final CloudinaryUploader cloudinary;
    private final PetRepository pets;

    @Override
    public void run(ApplicationArguments args) {
        if (!cloudinary.enabled()) {
            return;
        }
        int moved = 0;
        for (Pet pet : pets.findByPhotoVersionGreaterThanAndPhotoUrlIsNull(0)) {
            try {
                String url = photos.uploadLegacyFile(pet.getId());
                if (url != null) {
                    pet.setPhotoUrl(url);
                    pets.save(pet);
                    moved++;
                }
            } catch (RuntimeException e) {
                log.warn("Chưa chuyển được ảnh thú cưng {} lên Cloudinary, thử lại lần khởi động sau: {}", pet.getId(),
                        e.getMessage());
            }
        }
        if (moved > 0) {
            log.info("Đã chuyển {} ảnh thú cưng cũ lên Cloudinary", moved);
        }
    }
}
