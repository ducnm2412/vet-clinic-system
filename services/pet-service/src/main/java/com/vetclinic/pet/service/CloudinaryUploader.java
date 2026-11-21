package com.vetclinic.pet.service;

import com.vetclinic.pet.exception.PhotoUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Đẩy ảnh lên Cloudinary bằng upload có ký (REST), không cần thêm thư viện. Chỉ bật khi có đủ ba biến
 * CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET; thiếu thì {@link #enabled()} là false và
 * {@link PetPhotoStorageService} giữ ảnh trên đĩa như trước.
 *
 * Chữ ký: SHA-1 của các tham số (trừ file, api_key) sắp theo tên, nối bằng "&", rồi nối thêm api secret.
 */
@Slf4j
@Component
public class CloudinaryUploader {

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final RestClient client;

    @Autowired
    public CloudinaryUploader(@Value("${cloudinary.cloud-name:}") String cloudName,
                              @Value("${cloudinary.api-key:}") String apiKey,
                              @Value("${cloudinary.api-secret:}") String apiSecret,
                              @Value("${cloudinary.api-base:https://api.cloudinary.com/v1_1}") String apiBase) {
        this(cloudName, apiKey, apiSecret, RestClient.builder().baseUrl(apiBase).build());
    }

    CloudinaryUploader(String cloudName, String apiKey, String apiSecret, RestClient client) {
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.client = client;
    }

    public boolean enabled() {
        return !cloudName.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    /**
     * Tải ảnh lên với {@code publicId} cố định: tải lại là ghi đè ảnh cũ và URL trả về mang số phiên bản mới
     * nên trình duyệt không giữ ảnh cũ.
     *
     * @return URL https của ảnh
     */
    public String upload(String publicId, byte[] bytes) {
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = sign("invalidate=true&overwrite=true&public_id=" + publicId + "&timestamp=" + timestamp);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "photo";
            }
        });
        form.add("api_key", apiKey);
        form.add("timestamp", String.valueOf(timestamp));
        form.add("public_id", publicId);
        form.add("overwrite", "true");
        form.add("invalidate", "true");
        form.add("signature", signature);

        try {
            Map<String, Object> body = client.post()
                    .uri("/{cloud}/image/upload", cloudName)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            Object url = body == null ? null : body.get("secure_url");
            if (url == null || url.toString().isBlank()) {
                throw new PhotoUploadException("Cloudinary không trả về URL ảnh", null);
            }
            return url.toString();
        } catch (RestClientException e) {
            // Không ghi thân request: có chữ ký và khoá.
            log.warn("Tải ảnh {} lên Cloudinary thất bại: {}", publicId, e.getMessage());
            throw new PhotoUploadException("Không tải được ảnh lên Cloudinary", e);
        }
    }

    /** Xoá ảnh khi hồ sơ bị xoá. Lỗi chỉ ghi log: ảnh mồ côi vô hại hơn là chặn việc xoá hồ sơ. */
    public void delete(String publicId) {
        if (!enabled()) {
            return;
        }
        long timestamp = System.currentTimeMillis() / 1000;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("public_id", publicId);
        form.add("timestamp", String.valueOf(timestamp));
        form.add("invalidate", "true");
        form.add("api_key", apiKey);
        form.add("signature", sign("invalidate=true&public_id=" + publicId + "&timestamp=" + timestamp));
        try {
            client.post()
                    .uri("/{cloud}/image/destroy", cloudName)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Không xoá được ảnh {} trên Cloudinary: {}", publicId, e.getMessage());
        }
    }

    private String sign(String toSign) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((toSign + apiSecret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 không có trong JVM này", e);
        }
    }
}
