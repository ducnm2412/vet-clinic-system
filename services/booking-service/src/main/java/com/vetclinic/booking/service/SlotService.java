package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.AppointmentSlotResponse;
import com.vetclinic.booking.dto.AvailableTimeResponse;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
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

    @Transactional
    public List<AppointmentSlotResponse> generateSlots(UUID doctorId, LocalDate date) {
        List<AppointmentSlotResponse> created = new ArrayList<>();

        for (LocalTime startTime : ClinicSchedule.SLOT_TIMES) {
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
        return appointmentSlotRepository.countAvailableByDate(date);
    }
}
