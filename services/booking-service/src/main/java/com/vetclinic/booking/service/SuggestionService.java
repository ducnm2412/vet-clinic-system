package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.SuggestedSlotResponse;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private static final int MAX_SUGGESTIONS = 3;

    private final AppointmentSlotRepository appointmentSlotRepository;

    // 2 bước: (1) cùng ngày, ưu tiên giờ gần với giờ khách muốn nhất; nếu ngày đó không còn
    // slot trống nào khác thì (2) mở sang ngày gần nhất còn trống đúng khung giờ đã chọn.
    @Transactional(readOnly = true)
    public List<SuggestedSlotResponse> findSuggestions(LocalDate date, LocalTime requestedTime) {
        List<SuggestedSlotResponse> sameDay = appointmentSlotRepository.countAvailableByDate(date).stream()
                .sorted(Comparator.comparingLong(t -> Math.abs(Duration.between(requestedTime, t.startTime()).toMinutes())))
                .limit(MAX_SUGGESTIONS)
                .map(t -> new SuggestedSlotResponse(date, t.startTime(), t.endTime()))
                .toList();

        if (!sameDay.isEmpty()) {
            return sameDay;
        }

        return appointmentSlotRepository.findNextAvailableDatesForTime(requestedTime, date).stream()
                .limit(MAX_SUGGESTIONS)
                .map(d -> new SuggestedSlotResponse(d, requestedTime,
                        requestedTime.plusMinutes(ClinicSchedule.SLOT_DURATION_MINUTES)))
                .toList();
    }
}
