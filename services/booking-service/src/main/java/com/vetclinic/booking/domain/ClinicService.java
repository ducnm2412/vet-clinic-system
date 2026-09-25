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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CN-18, VD-21: một dịch vụ của phòng khám (khám tổng quát, tiêm phòng, phẫu thuật…).
 *
 * Tên lớp là ClinicService chứ không phải Service: trong dự án này "service" còn nghĩa là
 * microservice, và package {@code service} đã có sẵn.
 */
@Entity
@Table(name = "clinic_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Ổn định theo thời gian: frontend chọn biểu tượng theo slug, đổi tên hiển thị không sao. */
    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Để nhân viên ước lượng và hiện cho khách. Chưa dùng để chia khung giờ — mọi ca vẫn 30 phút. */
    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 30;

    /** Có thể để trống: nhiều ca phải khám xong mới báo giá được. */
    @Column(name = "reference_price", precision = 12, scale = 2)
    private BigDecimal referencePrice;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
