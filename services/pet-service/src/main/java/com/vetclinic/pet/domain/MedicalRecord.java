package com.vetclinic.pet.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bệnh án của một lượt khám, kèm đơn thuốc. Một lịch hẹn có tối đa một bệnh án.
 *
 * {@code appointmentId} là UUID thường — lịch hẹn nằm ở booking-service, khác database nên không
 * có khoá ngoại. Còn {@code petId} có khoá ngoại thật tới {@code pets} vì thú cưng ở ngay đây.
 *
 * {@code customerUserId} và {@code doctorUserId} chép lại lúc lập bệnh án, không hỏi lại
 * booking-service mỗi lần đọc: bệnh án phải đọc được cả khi booking-service đang tắt.
 */
@Entity
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true, updatable = false)
    private UUID appointmentId;

    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private UUID customerUserId;

    @Column(name = "doctor_user_id", nullable = false, updatable = false)
    private UUID doctorUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String treatment;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PrescriptionStatus status = PrescriptionStatus.PENDING;

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PrescriptionItem> prescriptionItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
