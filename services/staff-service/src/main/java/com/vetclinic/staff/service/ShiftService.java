package com.vetclinic.staff.service;

import com.vetclinic.staff.domain.Shift;
import com.vetclinic.staff.dto.Shifts;
import com.vetclinic.staff.exception.StaffExceptions;
import com.vetclinic.staff.messaging.ShiftChangedEvent;
import com.vetclinic.staff.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CN-39: xếp ca trực. Chỉ ADMIN.
 *
 * Ca của bác sĩ quyết định giờ khám mở ra cho khách: booking-service nghe sự kiện ở đây và chỉ
 * sinh khung giờ trong phạm vi ca. Bác sĩ không có ca thì ngày đó khách không đặt được — đó là
 * điểm chốt của VD-11 (trước đây mọi bác sĩ đều có lịch mọi ngày).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    static final long MAX_RANGE_DAYS = 366;

    private final ShiftRepository shiftRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Shifts.CreateResult create(Shifts.CreateRequest request, UUID actorId) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new StaffExceptions.InvalidRequestException("Giờ kết thúc phải sau giờ bắt đầu");
        }

        List<Shifts.Response> created = new ArrayList<>();
        List<LocalDate> skipped = new ArrayList<>();

        for (LocalDate date : request.dates().stream().distinct().sorted().toList()) {
            // Xếp lại đúng ca đã có thì bỏ qua, không báo lỗi: admin bấm lại cả tuần là chuyện thường.
            if (shiftRepository.existsByUserIdAndDateAndStartTime(request.userId(), date, request.startTime())) {
                skipped.add(date);
                continue;
            }

            Shift shift = shiftRepository.saveAndFlush(Shift.builder()
                    .userId(request.userId()).date(date)
                    .startTime(request.startTime()).endTime(request.endTime())
                    .note(request.note()).createdBy(actorId).build());
            created.add(toResponse(shift));
            eventPublisher.publishEvent(ShiftChangedEvent.added(shift.getUserId(), shift.getDate(),
                    shift.getStartTime(), shift.getEndTime()));
        }

        log.info("Xếp ca cho {}: {} ngày mới, {} ngày đã có sẵn", request.userId(), created.size(), skipped.size());
        return new Shifts.CreateResult(created, skipped);
    }

    @Transactional
    public void delete(UUID shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new StaffExceptions.NotFoundException("Không tìm thấy ca trực: " + shiftId));

        shiftRepository.delete(shift);
        eventPublisher.publishEvent(ShiftChangedEvent.removed(shift.getUserId(), shift.getDate(),
                shift.getStartTime(), shift.getEndTime()));
    }

    @Transactional(readOnly = true)
    public List<Shifts.Response> search(LocalDate from, LocalDate to, UUID userId) {
        validateRange(from, to);
        return shiftRepository.search(from, to, userId).stream().map(ShiftService::toResponse).toList();
    }

    static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new StaffExceptions.InvalidRequestException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new StaffExceptions.InvalidRequestException("Chỉ xem được tối đa " + MAX_RANGE_DAYS + " ngày một lần");
        }
    }

    private static Shifts.Response toResponse(Shift shift) {
        return new Shifts.Response(shift.getId(), shift.getUserId(), shift.getDate(),
                shift.getStartTime(), shift.getEndTime(), shift.getNote());
    }
}
