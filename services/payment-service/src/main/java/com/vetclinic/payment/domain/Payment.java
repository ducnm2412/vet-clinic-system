package com.vetclinic.payment.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 1 bệnh án (đơn thuốc) chỉ có tối đa 1 payment — khớp PrescriptionCreatedEvent.medicalRecordId.
    @Column(name = "medical_record_id", nullable = false, unique = true)
    private UUID medicalRecordId;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "customer_user_id", nullable = false)
    private UUID customerUserId;

    // NULL khi mới tạo từ prescription.created (status PENDING_AMOUNT) — chưa có price catalog
    // ở booking-service nên staff phải nhập tay; chuyển sang PENDING sau khi nhập xong.
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    // NULL cho tới khi khách hàng/staff chọn phương thức lúc thanh toán.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING_AMOUNT;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
