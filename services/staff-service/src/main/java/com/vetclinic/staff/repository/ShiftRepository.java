package com.vetclinic.staff.repository;

import com.vetclinic.staff.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    boolean existsByUserIdAndDateAndStartTime(UUID userId, LocalDate date, java.time.LocalTime startTime);

    List<Shift> findByUserIdAndDate(UUID userId, LocalDate date);

    /** Lịch làm việc trong khoảng; userId null = tất cả mọi người. */
    @Query("SELECT s FROM Shift s WHERE s.date BETWEEN :from AND :to "
            + "AND (CAST(:userId AS string) IS NULL OR s.userId = :userId) "
            + "ORDER BY s.date, s.startTime, s.userId")
    List<Shift> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                       @Param("userId") UUID userId);
}
