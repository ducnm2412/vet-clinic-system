package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findBySlotId(UUID slotId);

    List<Appointment> findByCustomerUserId(UUID customerUserId);

    // Dùng chung cho cả 2 màn "list filter": bác sĩ xem lịch của chính mình (truyền doctorUserId
    // = userId của họ) và staff/admin lọc toàn bộ (mọi tham số có thể null = bỏ qua điều kiện đó).
    // CAST(... AS ...) tường minh: PostgreSQL JDBC không tự suy được kiểu tham số khi nó chỉ
    // xuất hiện độc lập trong "? IS NULL" (không có ngữ cảnh cột nào để suy kiểu), ném lỗi
    // "could not determine data type of parameter" nếu không cast.
    @Query("SELECT a FROM Appointment a WHERE (CAST(:date AS date) IS NULL OR a.slot.date = :date) " +
            "AND (CAST(:status AS string) IS NULL OR a.status = :status) " +
            "AND (CAST(:doctorUserId AS string) IS NULL OR a.slot.doctorUserId = :doctorUserId) " +
            "ORDER BY a.slot.date, a.slot.startTime")
    List<Appointment> search(@Param("date") LocalDate date, @Param("status") AppointmentStatus status,
                              @Param("doctorUserId") UUID doctorUserId);

    // Dùng để auto-assign: khi nhiều bác sĩ cùng rảnh 1 khung giờ, ưu tiên bác sĩ có ít lịch hẹn
    // ĐANG active nhất trong ngày đó (loại CANCELLED — appointment đã huỷ không tính là tải).
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.slot.doctorUserId = :doctorUserId " +
            "AND a.slot.date = :date AND a.status <> com.vetclinic.booking.domain.AppointmentStatus.CANCELLED")
    long countActiveAppointmentsForDoctorOnDate(@Param("doctorUserId") UUID doctorUserId,
                                                 @Param("date") LocalDate date);
}
