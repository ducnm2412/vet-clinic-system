package com.vetclinic.staff.scheduler;

import com.vetclinic.staff.repository.AttendanceRecordRepository;
import com.vetclinic.staff.service.AttendanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Quét định kỳ và tự đóng các ngày công đã qua mà người đi làm quên bấm "Ra ca", để ca không treo "Chưa ra ca"
 * mãi và có giờ công để tính. Quét theo giờ (không hẹn đúng nửa đêm) nên máy chủ tắt qua đêm thì lượt sau tự bù.
 * Đặt {@code attendance.auto-close-enabled=false} để tắt.
 */
@Slf4j
@Component
public class AttendanceAutoCloseJob {

    // Mỗi lượt xử lý tối đa chừng này bản ghi; phần còn lại để lượt sau.
    static final int BATCH_SIZE = 200;

    private final AttendanceRecordRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final boolean enabled;

    public AttendanceAutoCloseJob(AttendanceRecordRepository attendanceRepository,
                                  AttendanceService attendanceService,
                                  @Value("${attendance.auto-close-enabled:true}") boolean enabled) {
        this.attendanceRepository = attendanceRepository;
        this.attendanceService = attendanceService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${attendance.auto-close-scan-interval-ms:3600000}",
            initialDelayString = "${attendance.auto-close-initial-delay-ms:60000}")
    public void run() {
        if (!enabled) {
            return;
        }
        List<UUID> ids = attendanceRepository.findOpenIdsBefore(
                LocalDate.now(AttendanceService.CLINIC_ZONE), PageRequest.ofSize(BATCH_SIZE));
        int closed = 0;
        for (UUID id : ids) {
            try {
                if (attendanceService.autoClose(id)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                // Một bản ghi lỗi không được chặn các bản còn lại; lượt sau sẽ thử lại.
                log.error("Không tự đóng được ngày công {}", id, e);
            }
        }
        if (closed > 0) {
            log.info("Đã tự đóng {} ngày công quên ra ca", closed);
        }
    }
}
