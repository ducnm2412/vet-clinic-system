package com.vetclinic.booking.service;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

// Giờ làm việc cố định giống hệt nhau cho mọi bác sĩ (ClinicSchedule.SLOT_TIMES) nên không cần
// staff/admin chủ động gọi tay POST /booking/slots/generate cho từng bác sĩ — tự động sinh sẵn
// slot cho TOÀN BỘ bác sĩ đang có trong profile-service, chạy 1 lần lúc service khởi động (đỡ
// phải chờ qua đêm mới test được) và lặp lại mỗi đêm để luôn có đủ slot N ngày tới.
// Endpoint POST /booking/slots/generate vẫn giữ lại cho trường hợp cần sinh ngay không đợi job
// (vd vừa thêm bác sĩ mới giữa chừng).
// @ConditionalOnProperty tắt bean này trong test (src/test/resources/application.yml) — nếu
// không, mọi @SpringBootTest sẽ vô tình boot luôn scheduler này; khi chạy nhiều test class liên
// tiếp vượt quá initialDelay 45s, nó thật sự chèn dữ liệu vào DB test, đè lên các ngày cố định
// mà test dùng để assert.
@Slf4j
@Component
@ConditionalOnProperty(name = "app.scheduling.slot-generation.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SlotGenerationScheduler {

    private final SlotService slotService;
    private final ProfileServiceClient profileServiceClient;

    // initialDelay chờ qua mốc ~30-40s Eureka propagation quen thuộc của dự án này trước khi
    // gọi Feign lần đầu; fixedDelay = MAX_VALUE để trigger này chỉ thực sự chạy đúng 1 lần lúc
    // khởi động, việc lặp lại định kỳ giao hẳn cho generateNightly() bên dưới.
    @Scheduled(initialDelay = 45_000, fixedDelay = Long.MAX_VALUE)
    public void generateOnStartup() {
        generateSlotsForAllDoctors();
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void generateNightly() {
        generateSlotsForAllDoctors();
    }

    private void generateSlotsForAllDoctors() {
        List<DoctorSummaryResponse> doctors;
        try {
            doctors = profileServiceClient.listDoctors();
        } catch (Exception e) {
            log.warn("Không lấy được danh sách bác sĩ từ profile-service, bỏ qua lượt auto-generate slot này", e);
            return;
        }

        LocalDate today = LocalDate.now();
        int totalCreated = 0;
        for (DoctorSummaryResponse doctor : doctors) {
            for (int i = 0; i < ClinicSchedule.SLOT_GENERATION_HORIZON_DAYS; i++) {
                totalCreated += slotService.generateSlots(doctor.userId(), today.plusDays(i)).size();
            }
        }

        log.info("Auto-generated {} slot mới cho {} bác sĩ, {} ngày tới (idempotent — bỏ qua slot đã có)",
                totalCreated, doctors.size(), ClinicSchedule.SLOT_GENERATION_HORIZON_DAYS);
    }
}
