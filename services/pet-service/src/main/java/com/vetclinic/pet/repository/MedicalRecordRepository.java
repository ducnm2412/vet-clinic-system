package com.vetclinic.pet.repository;

import com.vetclinic.pet.domain.MedicalRecord;
import com.vetclinic.pet.domain.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

    /** Lịch sử khám của một con vật — mới nhất trước (CN-24). */
    List<MedicalRecord> findByPetIdOrderByCreatedAtDesc(UUID petId);

    /** Hàng đợi của quầy: đơn thuốc đã trả tiền, chờ giao — cũ nhất trước. */
    List<MedicalRecord> findByStatusOrderByCreatedAtAsc(PrescriptionStatus status);

    /**
     * Đơn thuốc còn dở của riêng một bác sĩ (PENDING — khách chưa trả tiền, hoặc PAID — đã trả
     * nhưng chưa giao). Trước đây phải join sang appointments và slots để biết bác sĩ nào kê;
     * giờ doctor_user_id nằm sẵn trên bệnh án nên chỉ là một câu lọc thường.
     */
    List<MedicalRecord> findByDoctorUserIdAndStatusInOrderByCreatedAtAsc(UUID doctorUserId,
                                                                        Collection<PrescriptionStatus> statuses);

    boolean existsByPetId(UUID petId);
}
