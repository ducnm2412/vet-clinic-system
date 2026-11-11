package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
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
    // LEFT JOIN: lịch chờ xếp bác sĩ chưa có slot — JOIN thường (a.slot.date) sẽ làm chúng biến mất.
    // Ngày/giờ của lịch chưa có slot là giờ khách mong muốn. Lọc theo bác sĩ thì lịch chưa có
    // slot không khớp (chưa ai phụ trách), nên bác sĩ không thấy lịch còn chờ xếp.
    @Query("SELECT a FROM Appointment a LEFT JOIN a.slot s " +
            "WHERE (CAST(:date AS date) IS NULL OR COALESCE(s.date, a.preferredDate) = :date) " +
            "AND (CAST(:status AS string) IS NULL OR a.status = :status) " +
            "AND (CAST(:doctorUserId AS string) IS NULL OR s.doctorUserId = :doctorUserId) " +
            "ORDER BY COALESCE(s.date, a.preferredDate), COALESCE(s.startTime, a.preferredTime)")
    List<Appointment> search(@Param("date") LocalDate date, @Param("status") AppointmentStatus status,
                              @Param("doctorUserId") UUID doctorUserId);

    // Dùng để auto-assign: khi nhiều bác sĩ cùng rảnh 1 khung giờ, ưu tiên bác sĩ có ít lịch hẹn
    // ĐANG active nhất trong ngày đó (loại CANCELLED — appointment đã huỷ không tính là tải).
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.slot.doctorUserId = :doctorUserId " +
            "AND a.slot.date = :date AND a.status <> com.vetclinic.booking.domain.AppointmentStatus.CANCELLED")
    long countActiveAppointmentsForDoctorOnDate(@Param("doctorUserId") UUID doctorUserId,
                                                 @Param("date") LocalDate date);

    // CN-47: số liệu lịch khám cho reporting-service, tính theo NGÀY KHÁM (ngày của slot), không
    // theo ngày đặt. Mỗi dòng: [khoá, tổng, PENDING, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW].
    // Lịch còn chờ xếp bác sĩ hoặc chờ khách đồng ý cũng là "chờ xử lý".
    String STATUS_COUNTS = "COUNT(*), " +
            "COUNT(*) FILTER (WHERE a.status IN ('PENDING', 'AWAITING_ASSIGNMENT', 'AWAITING_CUSTOMER')), " +
            "COUNT(*) FILTER (WHERE a.status = 'CONFIRMED'), " +
            "COUNT(*) FILTER (WHERE a.status = 'COMPLETED'), COUNT(*) FILTER (WHERE a.status = 'CANCELLED'), " +
            "COUNT(*) FILTER (WHERE a.status = 'NO_SHOW') ";

    // LEFT JOIN + COALESCE: lịch chưa có slot tính theo ngày khách mong muốn.
    @Query(value = "SELECT COALESCE(s.date, a.preferred_date), " + STATUS_COUNTS +
            "FROM appointments a LEFT JOIN appointment_slots s ON s.id = a.slot_id " +
            "WHERE COALESCE(s.date, a.preferred_date) BETWEEN :from AND :to " +
            "GROUP BY COALESCE(s.date, a.preferred_date)", nativeQuery = true)
    List<Object[]> countByDayAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "SELECT s.doctor_user_id, " + STATUS_COUNTS +
            "FROM appointments a JOIN appointment_slots s ON s.id = a.slot_id " +
            "WHERE s.date BETWEEN :from AND :to GROUP BY s.doctor_user_id", nativeQuery = true)
    List<Object[]> countByDoctorAndStatus(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ---- Đặt lịch theo yêu cầu ----

    // Một lịch "chiếm" khung giờ (date, time) khi chưa huỷ và: đã có slot ở giờ đó (kể cả lịch chờ
    // khách đồng ý — giờ đề xuất đã giữ sẵn slot của bác sĩ), hoặc đang chờ xếp bác sĩ và khách
    // mong muốn đúng giờ đó.
    String OCCUPIES_TIME = "a.status <> com.vetclinic.booking.domain.AppointmentStatus.CANCELLED AND (" +
            "(s IS NOT NULL AND s.date = :date AND s.startTime = :time) " +
            "OR (s IS NULL AND a.status = com.vetclinic.booking.domain.AppointmentStatus.AWAITING_ASSIGNMENT " +
            "AND a.preferredDate = :date AND a.preferredTime = :time))";

    /** Số lịch đang chiếm khung giờ này — so với số bác sĩ để biết còn nhận được yêu cầu không. */
    @Query("SELECT COUNT(a) FROM Appointment a LEFT JOIN a.slot s WHERE " + OCCUPIES_TIME)
    long countOccupying(@Param("date") LocalDate date, @Param("time") LocalTime time);

    /** Số lịch đang chiếm khung giờ này của riêng một con vật — một bé không khám hai nơi cùng lúc. */
    @Query("SELECT COUNT(a) FROM Appointment a LEFT JOIN a.slot s WHERE a.petId = :petId AND " + OCCUPIES_TIME)
    long countOccupyingForPet(@Param("petId") UUID petId, @Param("date") LocalDate date,
                              @Param("time") LocalTime time);

    /** Lịch đang chờ phòng khám/khách xử lý, theo giờ khám tăng dần (admin xử lý cái gần nhất trước). */
    @Query("SELECT a FROM Appointment a LEFT JOIN a.slot s WHERE a.status IN :statuses " +
            "ORDER BY COALESCE(s.date, a.preferredDate), COALESCE(s.startTime, a.preferredTime), a.createdAt")
    List<Appointment> findAwaiting(@Param("statuses") Collection<AppointmentStatus> statuses);

    /** Khoá hàng cho tới hết transaction: hai admin cùng xếp một lịch thì người sau thấy trạng thái mới. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appointment a WHERE a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") UUID id);

    long countByCustomerUserIdAndStatusIn(UUID customerUserId, Collection<AppointmentStatus> statuses);

    /**
     * Khoá cố vấn (advisory lock) theo khoá số, giữ tới hết transaction. Đếm rồi mới thêm là hai bước: hai yêu cầu cùng
     * lúc có thể cùng thấy "còn một chỗ". Khoá này làm các yêu cầu cùng khung giờ nối đuôi nhau,
     * khung giờ khác nhau vẫn chạy song song.
     */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(:key) AS text)", nativeQuery = true)
    String advisoryLock(@Param("key") long key);
}
