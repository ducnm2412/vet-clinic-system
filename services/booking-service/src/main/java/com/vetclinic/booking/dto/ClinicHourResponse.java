package com.vetclinic.booking.dto;

import java.time.LocalTime;

/**
 * Một mốc giờ khách có thể chọn khi gửi yêu cầu đặt lịch.
 *
 * @param remaining số yêu cầu còn nhận được ở mốc này (bằng số bác sĩ đang làm việc trừ số lịch
 *                  đã chiếm giờ đó); 0 nghĩa là mốc đã đầy hoặc đã qua.
 */
public record ClinicHourResponse(LocalTime startTime, LocalTime endTime, boolean available, long remaining) {
}
