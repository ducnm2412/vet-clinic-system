package com.vetclinic.staff.repository;

import com.vetclinic.staff.domain.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaveRepository extends JpaRepository<Leave, UUID> {

    boolean existsByUserIdAndDate(UUID userId, LocalDate date);

    List<Leave> findByUserIdAndDateIn(UUID userId, java.util.Collection<LocalDate> dates);

    /** Ngày nghỉ trong khoảng; userId null = tất cả mọi người. */
    @Query("SELECT l FROM Leave l WHERE l.date BETWEEN :from AND :to "
            + "AND (CAST(:userId AS string) IS NULL OR l.userId = :userId) "
            + "ORDER BY l.date, l.userId")
    List<Leave> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                       @Param("userId") UUID userId);
}
