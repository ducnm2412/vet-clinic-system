package com.vetclinic.staff.messaging;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * CN-41: ca trực vừa được xếp thêm hoặc bỏ đi.
 *
 * booking-service nghe để mở / đóng khung giờ khám của bác sĩ ngay, không phải đợi tới lượt sinh
 * slot hằng đêm. Kèm sẵn giờ bắt đầu và kết thúc để bên kia không phải hỏi ngược lại.
 */
public record ShiftChangedEvent(UUID userId, LocalDate date, LocalTime startTime, LocalTime endTime, boolean added) {

    public static ShiftChangedEvent added(UUID userId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        return new ShiftChangedEvent(userId, date, startTime, endTime, true);
    }

    public static ShiftChangedEvent removed(UUID userId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        return new ShiftChangedEvent(userId, date, startTime, endTime, false);
    }
}
