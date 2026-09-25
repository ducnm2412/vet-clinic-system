package com.vetclinic.pet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * CN-19: nhân viên lập hồ sơ thú cưng hộ khách đang đứng ở quầy.
 *
 * Chủ nuôi nằm trong thân request chứ không lấy từ token — đây là nhánh duy nhất như vậy, và chỉ
 * STAFF/ADMIN gọi được. Khách tự thêm thì vẫn đi /pets/me, chủ nuôi lấy từ token.
 */
public record PetForOwnerRequest(
        @NotNull UUID ownerUserId,
        @NotNull @Valid PetRequest pet
) {
}
