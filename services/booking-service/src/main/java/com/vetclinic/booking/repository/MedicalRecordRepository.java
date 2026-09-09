package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.MedicalRecord;
import com.vetclinic.booking.domain.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

    // Hàng đợi đơn thuốc chờ staff tiếp nhận.
    List<MedicalRecord> findByStatusOrderByCreatedAtAsc(PrescriptionStatus status);
}
