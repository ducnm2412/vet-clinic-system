package com.vetclinic.staff.service;

import com.vetclinic.staff.domain.Leave;
import com.vetclinic.staff.domain.Shift;
import com.vetclinic.staff.dto.Leaves;
import com.vetclinic.staff.exception.StaffExceptions;
import com.vetclinic.staff.messaging.ShiftChangedEvent;
import com.vetclinic.staff.repository.LeaveRepository;
import com.vetclinic.staff.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin ghi tay ngày nghỉ khi có người báo nghỉ (không có đơn xin nghỉ). Ghi nghỉ thì ca đã xếp của người
 * đó trong ngày bị gỡ, kèm sự kiện để booking-service đóng các giờ khám còn trống của bác sĩ. Lịch khách đã
 * đặt vẫn giữ nguyên: admin xử lý tiếp ở màn Lịch khám.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final ShiftRepository shiftRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Leaves.CreateResult create(Leaves.CreateRequest request, UUID actorId) {
        List<Leaves.Response> created = new ArrayList<>();
        List<LocalDate> skipped = new ArrayList<>();
        int removedShifts = 0;

        for (LocalDate date : request.dates().stream().distinct().sorted().toList()) {
            // Ghi lại ngày đã nghỉ thì bỏ qua, không báo lỗi.
            if (leaveRepository.existsByUserIdAndDate(request.userId(), date)) {
                skipped.add(date);
                continue;
            }
            Leave leave = leaveRepository.saveAndFlush(Leave.builder()
                    .userId(request.userId()).date(date)
                    .reason(blankToNull(request.reason())).createdBy(actorId).build());
            created.add(toResponse(leave));

            for (Shift shift : shiftRepository.findByUserIdAndDate(request.userId(), date)) {
                shiftRepository.delete(shift);
                eventPublisher.publishEvent(ShiftChangedEvent.removed(shift.getUserId(), shift.getDate(),
                        shift.getStartTime(), shift.getEndTime()));
                removedShifts++;
            }
        }

        log.info("Ghi nghỉ cho {}: {} ngày mới, {} ngày đã có, gỡ {} ca", request.userId(), created.size(),
                skipped.size(), removedShifts);
        return new Leaves.CreateResult(created, skipped, removedShifts);
    }

    @Transactional
    public void delete(UUID leaveId) {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new StaffExceptions.NotFoundException("Không tìm thấy ngày nghỉ: " + leaveId));
        leaveRepository.delete(leave);
    }

    @Transactional(readOnly = true)
    public List<Leaves.Response> search(LocalDate from, LocalDate to, UUID userId) {
        ShiftService.validateRange(from, to);
        return leaveRepository.search(from, to, userId).stream().map(LeaveService::toResponse).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Leaves.Response toResponse(Leave leave) {
        return new Leaves.Response(leave.getId(), leave.getUserId(), leave.getDate(), leave.getReason());
    }
}
