package com.vetclinic.booking.messaging;

import com.vetclinic.booking.service.DoctorShiftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * CN-39, CN-41: staff-service xếp hoặc bỏ ca trực thì giờ khám mở ra / đóng lại ngay, không phải
 * đợi tới lượt sinh slot hằng đêm.
 *
 * Sự kiện gửi cho MỌI nhân sự; ca của nhân viên quầy không liên quan tới giờ khám, nhưng ở đây
 * không biết ai là bác sĩ — lưu hết cũng vô hại, vì chỉ bác sĩ mới có khung giờ khám được sinh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftChangedListener {

    private final DoctorShiftService doctorShiftService;

    @RabbitListener(queues = RabbitMQConfig.STAFF_SHIFT_QUEUE)
    public void onShiftChanged(ShiftChangedEvent event) {
        log.info("Nhận sự kiện ca trực: user={} ngày={} added={}", event.userId(), event.date(), event.added());
        if (event.added()) {
            doctorShiftService.addShift(event.userId(), event.date(), event.startTime(), event.endTime());
        } else {
            doctorShiftService.removeShift(event.userId(), event.date(), event.startTime(), event.endTime());
        }
    }
}
