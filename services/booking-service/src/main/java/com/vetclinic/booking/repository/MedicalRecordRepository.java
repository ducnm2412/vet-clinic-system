package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.MedicalRecord;
import com.vetclinic.booking.domain.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

    // Hàng đợi đơn thuốc (thường gọi với status = PAID) chờ staff tiếp nhận.
    List<MedicalRecord> findByStatusOrderByCreatedAtAsc(PrescriptionStatus status);

    // Đơn thuốc còn dở của riêng 1 bác sĩ (PENDING — chưa trả tiền, hoặc PAID — đã trả nhưng
    // chưa phát) — dùng cho trang "Bệnh án còn treo" của bác sĩ, khác hẳn hàng đợi PAID dùng
    // chung cho mọi bác sĩ mà staff xem ở trên.
    @Query("SELECT m FROM MedicalRecord m WHERE m.status IN :statuses AND m.appointment.slot.doctorUserId = :doctorId " +
            "ORDER BY m.createdAt ASC")
    List<MedicalRecord> findByStatusInAndDoctorUserId(@Param("statuses") List<PrescriptionStatus> statuses,
                                                        @Param("doctorId") UUID doctorId);
}
