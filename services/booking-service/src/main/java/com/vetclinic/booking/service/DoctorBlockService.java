package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.BlockedDoctor;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.BlockedDoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * CN-08: bác sĩ bị khoá tài khoản thì không nhận thêm lịch mới.
 *
 * - Khoá: slot trống từ hôm nay trở đi chuyển BLOCKED (khách không thấy, hệ thống không xếp vào),
 *   và bác sĩ vào danh sách chặn để lượt sinh slot hằng đêm bỏ qua.
 * - Mở khoá: BLOCKED trở lại AVAILABLE, sinh bù slot cho những ngày bị bỏ qua lúc đang khoá.
 *
 * Lịch hẹn ĐÃ đặt với bác sĩ đó giữ nguyên — nhân viên tự liên hệ khách để đổi hoặc huỷ; tự huỷ
 * hàng loạt ở đây là quyết định thay khách.
 * Cả hai thao tác chạy lại nhiều lần vẫn cho cùng kết quả (RabbitMQ có thể giao trùng).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorBlockService {

    private final BlockedDoctorRepository blockedDoctorRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final SlotService slotService;

    @Transactional
    public void block(UUID doctorUserId) {
        if (!blockedDoctorRepository.existsById(doctorUserId)) {
            blockedDoctorRepository.save(new BlockedDoctor(doctorUserId, Instant.now()));
        }
        int blocked = appointmentSlotRepository.changeFutureSlotStatus(doctorUserId, SlotStatus.AVAILABLE,
                SlotStatus.BLOCKED, LocalDate.now());
        log.info("Chặn bác sĩ {}: {} slot trống bị khoá", doctorUserId, blocked);
    }

    @Transactional
    public void unblock(UUID doctorUserId) {
        blockedDoctorRepository.deleteById(doctorUserId);
        LocalDate today = LocalDate.now();
        int reopened = appointmentSlotRepository.changeFutureSlotStatus(doctorUserId, SlotStatus.BLOCKED,
                SlotStatus.AVAILABLE, today);
        int created = 0;
        for (int i = 0; i < ClinicSchedule.SLOT_GENERATION_HORIZON_DAYS; i++) {
            created += slotService.generateSlots(doctorUserId, today.plusDays(i)).size();
        }
        log.info("Mở chặn bác sĩ {}: mở lại {} slot, sinh bù {} slot", doctorUserId, reopened, created);
    }
}
