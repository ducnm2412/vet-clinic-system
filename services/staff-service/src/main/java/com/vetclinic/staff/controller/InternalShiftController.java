package com.vetclinic.staff.controller;

import com.vetclinic.staff.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Endpoint giữa các service — chỉ vào được bằng khoá nội bộ (xem InternalApiKeyFilter). */
@RestController
@RequestMapping("/internal/shifts")
@RequiredArgsConstructor
public class InternalShiftController {

    private final ShiftRepository shiftRepository;

    /**
     * Id những người có ca trong ngày (cả bác sĩ lẫn nhân viên; bên gọi tự lọc theo vai trò). order-service dùng
     * để chia đơn hàng mới cho nhân viên đang có ca.
     */
    @GetMapping("/user-ids")
    public List<UUID> userIdsOnShift(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return shiftRepository.findUserIdsWithShiftOn(date);
    }
}
