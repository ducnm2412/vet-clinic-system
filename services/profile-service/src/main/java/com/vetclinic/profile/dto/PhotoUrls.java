package com.vetclinic.profile.dto;

import java.util.UUID;

// Ảnh đã lên Cloudinary thì DB giữ URL của nó (kèm phiên bản của Cloudinary). Chưa có thì dựng URL ảnh trên đĩa
// từ (kind, id, version) — đổi route sau này chỉ phải sửa ở đây. `?v=` chỉ để trình duyệt bỏ cache khi ảnh được thay.
public final class PhotoUrls {

    public static final String PETS = "pets";
    public static final String DOCTORS = "doctors";
    public static final String STAFF = "staff";
    public static final String CUSTOMERS = "customers";

    private PhotoUrls() {
    }

    /** URL ảnh trả cho client: URL Cloudinary đã lưu nếu có, không thì URL ảnh cũ trên đĩa (hoặc null nếu chưa có ảnh). */
    public static String resolve(String storedUrl, String kind, UUID id, int version) {
        return storedUrl != null && !storedUrl.isBlank() ? storedUrl : of(kind, id, version);
    }

    public static String of(String kind, UUID id, int version) {
        return version > 0 ? "/profile/photos/" + kind + "/" + id + "?v=" + version : null;
    }
}
