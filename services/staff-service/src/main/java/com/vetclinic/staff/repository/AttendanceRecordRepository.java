package com.vetclinic.staff.repository;

import com.vetclinic.staff.domain.AttendanceRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByUserIdAndDate(UUID userId, LocalDate date);

    /** Id các ngày công đã qua mà vẫn chưa ra ca (cũ nhất trước). */
    @Query("SELECT a.id FROM AttendanceRecord a WHERE a.checkOutAt IS NULL AND a.date < :today ORDER BY a.date")
    List<UUID> findOpenIdsBefore(@Param("today") LocalDate today, Pageable pageable);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.date BETWEEN :from AND :to "
            + "AND (CAST(:userId AS string) IS NULL OR a.userId = :userId) "
            + "ORDER BY a.date DESC, a.checkInAt DESC")
    List<AttendanceRecord> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("userId") UUID userId);
}
