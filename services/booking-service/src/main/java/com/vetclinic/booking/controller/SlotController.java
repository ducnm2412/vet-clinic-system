package com.vetclinic.booking.controller;

import com.vetclinic.booking.dto.AppointmentSlotResponse;
import com.vetclinic.booking.dto.AvailableTimeResponse;
import com.vetclinic.booking.dto.GenerateSlotsRequest;
import com.vetclinic.booking.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @GetMapping("/booking/available-times")
    public List<AvailableTimeResponse> getAvailableTimes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slotService.getAvailableTimes(date);
    }

    // STAFF/ADMIN sinh khung giờ cố định (bước 4) cho 1 bác sĩ trong 1 ngày — idempotent, gọi
    // lại nhiều lần cho cùng doctorUserId/date không tạo trùng slot.
    @PostMapping("/booking/slots/generate")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<AppointmentSlotResponse> generateSlots(@Valid @RequestBody GenerateSlotsRequest request) {
        return slotService.generateSlots(request.doctorUserId(), request.date());
    }
}
