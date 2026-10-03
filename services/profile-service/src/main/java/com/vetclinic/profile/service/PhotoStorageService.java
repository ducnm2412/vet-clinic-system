package com.vetclinic.profile.service;

import com.vetclinic.profile.dto.PhotoUrls;
import com.vetclinic.profile.exception.InvalidPhotoException;
import com.vetclinic.profile.exception.ResourceNotFoundException;
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
import java.util.Set;
import java.util.UUID;

/**
 * Lưu ảnh đại diện của thú cưng, bác sĩ, nhân viên, khách hàng dưới {@code <upload.dir>/<kind>/<id>.<ext>}.
 * Mỗi đối tượng chỉ giữ một ảnh: tải ảnh mới là ghi đè ảnh cũ.
 */
@Service
public class PhotoStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Set<String> KINDS =
            Set.of(PhotoUrls.PETS, PhotoUrls.DOCTORS, PhotoUrls.STAFF, PhotoUrls.CUSTOMERS);

    private static final String[] EXTENSIONS = {"jpg", "png", "webp"};

    public record StoredPhoto(Resource resource, MediaType mediaType) {
    }

    private final Path root;

    public PhotoStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public void store(String kind, UUID id, MultipartFile file) {
        requireKind(kind);
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

            Path dir = root.resolve(kind);
            Files.createDirectories(dir);
            Path target = dir.resolve(id + "." + ext);
            Path temp = Files.createTempFile(dir, id + "-", ".tmp");
            try {
                Files.write(temp, bytes);
                // Xoá bản cũ khác đuôi (vd đổi từ png sang jpg) để không còn file mồ côi.
                for (String other : EXTENSIONS) {
                    Files.deleteIfExists(dir.resolve(id + "." + other));
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được ảnh", e);
        }
    }

    public StoredPhoto load(String kind, UUID id) {
        requireKind(kind);
        for (String ext : EXTENSIONS) {
            Path file = root.resolve(kind).resolve(id + "." + ext);
            if (Files.isRegularFile(file)) {
                return new StoredPhoto(new FileSystemResource(file), mediaTypeOf(ext));
            }
        }
        throw new ResourceNotFoundException("Photo not found: " + kind + "/" + id);
    }

    private static void requireKind(String kind) {
        if (!KINDS.contains(kind)) {
            throw new ResourceNotFoundException("Photo not found: " + kind);
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
