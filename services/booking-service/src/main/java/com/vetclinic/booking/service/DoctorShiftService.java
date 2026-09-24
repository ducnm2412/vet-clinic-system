package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.DoctorShift;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.DoctorShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * CN-39 / VD-11: ca trực quyết định giờ khám nào được mở.
 *
 * Trước đây mọi bác sĩ đều có đủ khung giờ mọi ngày, nên khách đặt được cả vào ngày bác sĩ nghỉ.
 * Giờ chỉ ngày nào có ca mới có giờ khám, và chỉ trong phạm vi ca.
 *
 * Nhận trùng sự kiện vẫn cho cùng kết quả — RabbitMQ có thể giao một message hai lần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorShiftService {

    private final DoctorShiftRepository doctorShiftRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final SlotService slotService;

    @Transactional
    public void addShift(UUID doctorUserId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        doctorShiftRepository.findByDoctorUserIdAndDateAndStartTime(doctorUserId, date, startTime)
                .ifPresentOrElse(existing -> existing.setEndTime(endTime),
                        () -> doctorShiftRepository.saveAndFlush(DoctorShift.builder()
                                .doctorUserId(doctorUserId).date(date)
                                .startTime(startTime).endTime(endTime).build()));

        // Ca của hôm qua thì không mở giờ khám nữa, nhưng vẫn lưu để bảng công đối chiếu được.
        int created = date.isBefore(LocalDate.now()) ? 0 : slotService.generateSlots(doctorUserId, date).size();
        log.info("Ca mới của bác sĩ {} ngày {} ({}–{}): mở thêm {} khung giờ",
                doctorUserId, date, startTime, endTime, created);
    }

    @Transactional
    public void removeShift(UUID doctorUserId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        doctorShiftRepository.findByDoctorUserIdAndDateAndStartTime(doctorUserId, date, startTime)
                .ifPresent(doctorShiftRepository::delete);

        // Giờ trống trong ca vừa bỏ thì đóng lại. Giờ ĐÃ CÓ KHÁCH ĐẶT giữ nguyên — huỷ lịch của
        // khách là quyết định của con người, không phải hệ quả của một thao tác xếp ca.
        int closed = 0;
        for (var slot : appointmentSlotRepository.findByDoctorUserIdAndDate(doctorUserId, date)) {
            if (slot.getStatus() == SlotStatus.AVAILABLE && inRange(slot.getStartTime(), startTime, endTime)) {
                slot.setStatus(SlotStatus.CANCELLED);
                appointmentSlotRepository.saveAndFlush(slot);
                closed++;
            }
        }
        log.info("Bỏ ca của bác sĩ {} ngày {} ({}–{}): đóng {} khung giờ còn trống",
                doctorUserId, date, startTime, endTime, closed);
    }

    private static boolean inRange(LocalTime slotStart, LocalTime from, LocalTime to) {
        return !slotStart.isBefore(from) && slotStart.isBefore(to);
    }
}
