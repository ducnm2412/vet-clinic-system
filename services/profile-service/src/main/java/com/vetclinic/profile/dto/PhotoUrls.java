package com.vetclinic.profile.dto;

import java.util.UUID;

// URL ảnh luôn được dựng lại từ (kind, id, version) lúc trả response, không lưu chuỗi trong DB —
// đổi route sau này chỉ phải sửa ở đây. `?v=` chỉ để trình duyệt bỏ cache khi ảnh được thay.
public final class PhotoUrls {

    public static final String PETS = "pets";
    public static final String DOCTORS = "doctors";
    public static final String STAFF = "staff";
    public static final String CUSTOMERS = "customers";

    private PhotoUrls() {
    }

    public static String of(String kind, UUID id, int version) {
        return version > 0 ? "/profile/photos/" + kind + "/" + id + "?v=" + version : null;
    }
}
