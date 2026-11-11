package com.vetclinic.booking.dto;

import java.util.UUID;

/**
 * Một bác sĩ trong danh sách admin chọn khi xếp lịch.
 *
 * @param free     bác sĩ chưa có lịch khám vào giờ đó (chọn được)
 * @param onShift  giờ đó nằm trong ca trực đã xếp — chỉ để gợi ý, bác sĩ ngoài ca vẫn xếp được
 * @param dayLoad  số lịch bác sĩ đã có trong ngày, để chia đều việc
 */
public record DoctorAvailabilityResponse(
        UUID doctorUserId,
        String fullName,
        String specialty,
        boolean free,
        boolean onShift,
        long dayLoad
) {
}
