package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.dto.AvailableTimeResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, UUID> {

    // PESSIMISTIC_WRITE: khoá row ngay khi đọc, tránh 2 request cùng lúc đọc thấy slot còn
    // AVAILABLE rồi cả 2 cùng book được — request thứ 2 phải đợi request thứ 1 commit/rollback
    // trước khi đọc được, tránh double-booking.
    // ORDER BY doctorUserId: tiêu chí chọn slot đơn giản cho MVP khi có nhiều bác sĩ cùng rảnh
    // giờ đó — luôn ưu tiên deterministic thay vì phụ thuộc thứ tự không xác định của DB.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AppointmentSlot s WHERE s.date = :date AND s.startTime = :time AND s.status = 'AVAILABLE' " +
            "ORDER BY s.doctorUserId")
    List<AppointmentSlot> findAvailableForUpdate(@Param("date") LocalDate date, @Param("time") LocalTime time);

    List<AppointmentSlot> findByDoctorUserIdAndDate(UUID doctorUserId, LocalDate date);

    boolean existsByDoctorUserIdAndDateAndStartTime(UUID doctorUserId, LocalDate date, LocalTime startTime);

    // Gợi ý "mở ngày khác": ngày gần nhất sau :afterDate có ít nhất 1 bác sĩ còn trống đúng
    // khung giờ đang tìm.
    @Query("SELECT DISTINCT s.date FROM AppointmentSlot s " +
            "WHERE s.startTime = :time AND s.status = com.vetclinic.booking.domain.SlotStatus.AVAILABLE " +
            "AND s.date > :afterDate ORDER BY s.date ASC")
    List<LocalDate> findNextAvailableDatesForTime(@Param("time") LocalTime time, @Param("afterDate") LocalDate afterDate);

    @Query("SELECT new com.vetclinic.booking.dto.AvailableTimeResponse(s.startTime, s.endTime, COUNT(s)) " +
            "FROM AppointmentSlot s " +
            "WHERE s.date = :date AND s.status = com.vetclinic.booking.domain.SlotStatus.AVAILABLE " +
            "GROUP BY s.startTime, s.endTime " +
            "ORDER BY s.startTime")
    List<AvailableTimeResponse> countAvailableByDate(@Param("date") LocalDate date);
}
