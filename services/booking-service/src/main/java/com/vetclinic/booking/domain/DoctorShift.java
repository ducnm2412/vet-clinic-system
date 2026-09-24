package com.vetclinic.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * CN-39: ca trực của một bác sĩ trong một ngày, sao từ staff-service qua sự kiện.
 *
 * Nguồn sự thật nằm ở staff-service; bảng này chỉ để trả lời nhanh câu "hôm đó bác sĩ làm từ
 * mấy giờ tới mấy giờ" khi sinh khung giờ khám.
 */
@Entity
@Table(name = "doctor_shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorShift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Khung giờ khám bắt đầu lúc này có nằm trong ca không. */
    public boolean covers(LocalTime slotStart) {
        return !slotStart.isBefore(startTime) && slotStart.isBefore(endTime);
    }
}
