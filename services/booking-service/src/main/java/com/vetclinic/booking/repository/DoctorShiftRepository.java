package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.DoctorShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorShiftRepository extends JpaRepository<DoctorShift, UUID> {

    List<DoctorShift> findByDoctorUserIdAndDate(UUID doctorUserId, LocalDate date);

    Optional<DoctorShift> findByDoctorUserIdAndDateAndStartTime(UUID doctorUserId, LocalDate date, LocalTime startTime);
}
