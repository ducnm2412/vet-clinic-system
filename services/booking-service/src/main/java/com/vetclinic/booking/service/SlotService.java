package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.AppointmentSlotResponse;
import com.vetclinic.booking.dto.AvailableTimeResponse;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.domain.DoctorShift;
import com.vetclinic.booking.repository.BlockedDoctorRepository;
import com.vetclinic.booking.repository.DoctorShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotService {

    private final AppointmentSlotRepository appointmentSlotRepository;
    private final BlockedDoctorRepository blockedDoctorRepository;
    private final DoctorShiftRepository doctorShiftRepository;

    @Transactional
    public List<AppointmentSlotResponse> generateSlots(UUID doctorId, LocalDate date) {
        List<AppointmentSlotResponse> created = new ArrayList<>();
        // CN-08: bác sĩ đang bị khoá tài khoản thì không mở thêm giờ khám.
        if (blockedDoctorRepository.existsById(doctorId)) {
            return created;
        }

        // CN-39 / VD-11: chỉ mở giờ khám trong ca đã xếp. Ngày không có ca thì bác sĩ nghỉ, và
        // khách không đặt được — trước đây mọi bác sĩ đều có đủ khung giờ mọi ngày.
        List<DoctorShift> shifts = doctorShiftRepository.findByDoctorUserIdAndDate(doctorId, date);
        if (shifts.isEmpty()) {
            return created;
        }

        for (LocalTime startTime : ClinicSchedule.SLOT_TIMES) {
            if (shifts.stream().noneMatch(shift -> shift.covers(startTime))) {
                continue;
            }
            if (appointmentSlotRepository.existsByDoctorUserIdAndDateAndStartTime(doctorId, date, startTime)) {
                continue;
            }

            AppointmentSlot slot = AppointmentSlot.builder()
                    .doctorUserId(doctorId)
                    .date(date)
                    .startTime(startTime)
                    .endTime(startTime.plusMinutes(ClinicSchedule.SLOT_DURATION_MINUTES))
                    .build();

            created.add(toResponse(appointmentSlotRepository.saveAndFlush(slot)));
        }

        return created;
    }

    private AppointmentSlotResponse toResponse(AppointmentSlot slot) {
        return new AppointmentSlotResponse(slot.getId(), slot.getDoctorUserId(), slot.getDate(), slot.getStartTime(),
                slot.getEndTime(), slot.getStatus(), slot.getCreatedAt(), slot.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<AvailableTimeResponse> getAvailableTimes(LocalDate date) {
        List<AvailableTimeResponse> times = appointmentSlotRepository.countAvailableByDate(date);
        // Nếu ngày đang xem là hôm nay, bỏ những khung giờ đã bắt đầu (hoặc đang diễn ra) —
        // khách không thể đặt vào giờ đã trôi qua.
        if (date.isEqual(LocalDate.now(ClinicSchedule.ZONE_ID))) {
            LocalTime now = LocalTime.now(ClinicSchedule.ZONE_ID);
            times = times.stream().filter(t -> t.startTime().isAfter(now)).toList();
        }
        return times;
    }
}
