package com.vetclinic.pet.service;

import com.vetclinic.pet.exception.InvalidPhotoException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/**
 * Lưu ảnh đại diện thú cưng dưới {@code <upload.dir>/pets/<petId>.<ext>}. Mỗi con chỉ giữ một ảnh:
 * tải ảnh mới là ghi đè ảnh cũ. Cùng cấu trúc thư mục với profile-service trước đây, nên file cũ
 * chép sang volume của pet-service là dùng được ngay.
 */
@Service
public class PetPhotoStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final String[] EXTENSIONS = {"jpg", "png", "webp"};

    public record StoredPhoto(Resource resource, MediaType mediaType) {
    }

    private final Path dir;

    public PetPhotoStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.dir = Path.of(uploadDir).toAbsolutePath().normalize().resolve("pets");
    }

    public void store(UUID petId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidPhotoException("Chưa chọn ảnh để tải lên");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidPhotoException("Ảnh quá lớn, tối đa 5MB");
        }

        try {
            byte[] bytes = file.getBytes();
            // Kiểm tra theo nội dung thật của file, không tin Content-Type/tên file do client tự khai.
            String ext = detectExtension(bytes);
            if (ext == null) {
                throw new InvalidPhotoException("Chỉ nhận ảnh JPG, PNG hoặc WebP");
            }

            Files.createDirectories(dir);
            Path target = dir.resolve(petId + "." + ext);
            Path temp = Files.createTempFile(dir, petId + "-", ".tmp");
            try {
                Files.write(temp, bytes);
                // Xoá bản cũ khác đuôi (vd đổi từ png sang jpg) để không còn file mồ côi.
                for (String other : EXTENSIONS) {
                    Files.deleteIfExists(dir.resolve(petId + "." + other));
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được ảnh", e);
        }
    }

    public StoredPhoto load(UUID petId) {
        for (String ext : EXTENSIONS) {
            Path file = dir.resolve(petId + "." + ext);
            if (Files.isRegularFile(file)) {
                return new StoredPhoto(new FileSystemResource(file), mediaTypeOf(ext));
            }
        }
        throw new ResourceNotFoundException("Không có ảnh cho thú cưng: " + petId);
    }

    /** Thú cưng bị xoá thì ảnh đi theo. Lỗi đĩa không được làm hỏng việc xoá hồ sơ. */
    public void delete(UUID petId) {
        for (String ext : EXTENSIONS) {
            try {
                Files.deleteIfExists(dir.resolve(petId + "." + ext));
            } catch (IOException ignored) {
                // file mồ côi vô hại hơn là chặn xoá hồ sơ
            }
        }
    }

    private static MediaType mediaTypeOf(String ext) {
        return switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }

    private static String detectExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 12 && "RIFF".equals(ascii(b, 0)) && "WEBP".equals(ascii(b, 8))) {
            return "webp";
        }
        return null;
    }

    private static String ascii(byte[] b, int from) {
        return new String(Arrays.copyOfRange(b, from, from + 4), StandardCharsets.US_ASCII);
    }
}
