package com.vetclinic.booking.dto;

import com.vetclinic.booking.domain.AppointmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentDetailResponse(
        UUID id,
        UUID slotId,
        // VD-16: bác sĩ phụ trách. Lấy từ slot — khách không chọn bác sĩ, hệ thống tự xếp, nên
        // response là chỗ duy nhất người ta biết được mình được xếp cho ai.
        UUID doctorUserId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        UUID customerUserId,
        UUID petId,
        // Tên dịch vụ trả kèm để màn hình không phải gọi thêm lượt nữa; null khi khách không chọn.
        UUID serviceId,
        String serviceName,
        // Dịch vụ và giá (VND) dùng để tính tiền khám: dịch vụ khách chọn, không chọn thì dịch vụ khám mặc định
        // (ClinicServiceCatalog.DEFAULT_BILLING_SLUG). Tách khỏi serviceName để màn hình khách không hiện
        // một dịch vụ họ không chọn. Null khi dịch vụ chưa có giá hoặc không tìm được dịch vụ mặc định.
        String billingServiceName,
        BigDecimal billingServicePrice,
        String reason,
        AppointmentStatus status,
        Instant createdAt,
        Instant updatedAt,
        LocalDate proposedDate,
        LocalTime proposedStartTime,
        LocalDate preferredDate,
        LocalTime preferredStartTime,
        PetResponse pet
) {
}
