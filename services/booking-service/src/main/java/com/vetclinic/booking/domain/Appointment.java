package com.vetclinic.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 1 slot chỉ có tối đa 1 appointment ĐANG active tại một thời điểm — enforce bằng partial
    // unique index (WHERE status <> 'CANCELLED') ở migration V2, không phải unique toàn cột,
    // vì slot cần đặt lại được sau khi appointment cũ trên đó bị huỷ.
    //
    // Để trống với lịch đặt theo yêu cầu (AWAITING_*): chưa có bác sĩ nên chưa có slot, giờ khách
    // muốn nằm ở preferredDate/preferredTime. Dùng effectiveDate()/effectiveStartTime() khi chỉ
    // cần biết lịch này ở giờ nào, đừng tự rẽ nhánh theo slot ở từng chỗ.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private AppointmentSlot slot;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "preferred_time")
    private LocalTime preferredTime;

    /**
     * Email khách và tên bé chép lúc gửi yêu cầu, để báo khách khi admin xếp lịch (lúc đó không còn
     * token của khách). Để trống với lịch cũ hoặc lịch tạo hộ tại quầy.
     */
    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "pet_name")
    private String petName;

    /** Giờ admin đề xuất khi không có bác sĩ rảnh đúng giờ khách chọn. */
    @Column(name = "proposed_date")
    private LocalDate proposedDate;

    @Column(name = "proposed_time")
    private LocalTime proposedTime;

    // Liên kết tới userId/petId bên profile-service — khác DB nên không có FK thật.
    @Column(name = "customer_user_id", nullable = false)
    private UUID customerUserId;

    @Column(name = "pet_id", nullable = false)
    private UUID petId;

    /**
     * VD-21: dịch vụ khách cần. Để trống được — lịch hẹn cũ không có, và khách vẫn đặt được mà
     * chỉ ghi vào ô lý do khám.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ClinicService service;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Ngày khám: của slot nếu đã có, không thì ngày khách mong muốn. */
    public LocalDate effectiveDate() {
        return slot != null ? slot.getDate() : preferredDate;
    }

    public LocalTime effectiveStartTime() {
        return slot != null ? slot.getStartTime() : preferredTime;
    }

    public LocalTime effectiveEndTime() {
        return slot != null ? slot.getEndTime()
                : preferredTime == null ? null
                : preferredTime.plusMinutes(ClinicSchedule.SLOT_DURATION_MINUTES);
    }
}
