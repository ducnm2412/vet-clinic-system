package com.vetclinic.booking.dto;

import java.util.UUID;

/**
 * Một bác sĩ trong danh sách admin chọn khi xếp lịch.
 *
 * @param free     bác sĩ chưa có lịch khám vào giờ đó (chọn được)
 * @param onShift  giờ đó nằm trong ca làm việc đã xếp. Bác sĩ không có ca thì KHÔNG xếp được lịch (409);
 *                 chấm công không ảnh hưởng
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
