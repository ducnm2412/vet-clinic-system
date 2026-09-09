package com.vetclinic.booking.controller;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.support.TestJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentSlotRepository appointmentSlotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockBean
    private ProfileServiceClient profileServiceClient;

    private String customerToken(UUID userId) {
        return TestJwtSupport.token(jwtSecret, userId, List.of("CUSTOMER"));
    }

    // Bước 10 - phần lõi quan trọng nhất: khi 2 bác sĩ cùng rảnh đúng 1 khung giờ, hệ thống phải
    // auto-assign đúng bác sĩ đang có ÍT lịch hẹn active hơn trong ngày, không phải chọn ngẫu
    // nhiên/theo thứ tự UUID.
    @Test
    @Transactional
    void createAppointment_autoAssignsLeastBusyDoctor() throws Exception {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2027, 3, 1);
        LocalTime targetTime = LocalTime.of(9, 0);
        UUID busyDoctor = UUID.randomUUID();
        UUID freeDoctor = UUID.randomUUID();

        AppointmentSlot busyDoctorSlot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(busyDoctor).date(date).startTime(targetTime).endTime(targetTime.plusMinutes(30))
                .build());
        AppointmentSlot freeDoctorSlot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(freeDoctor).date(date).startTime(targetTime).endTime(targetTime.plusMinutes(30))
                .build());

        // busyDoctor đã có 2 appointment active khác trong CÙNG ngày (giờ khác) -> phải bị coi
        // là bận hơn freeDoctor (0 appointment).
        for (int i = 0; i < 2; i++) {
            AppointmentSlot otherSlot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                    .doctorUserId(busyDoctor).date(date)
                    .startTime(LocalTime.of(10 + i, 0)).endTime(LocalTime.of(10 + i, 30))
                    .status(SlotStatus.BOOKED)
                    .build());
            appointmentRepository.saveAndFlush(Appointment.builder()
                    .slot(otherSlot).customerUserId(UUID.randomUUID()).petId(UUID.randomUUID()).build());
        }

        mockMvc.perform(post("/booking/appointments")
                        .header("Authorization", "Bearer " + customerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"petId":"%s","date":"%s","startTime":"%s","reason":"Checkup"}
                                """.formatted(UUID.randomUUID(), date, targetTime)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(freeDoctorSlot.getId().toString()));

        AppointmentSlot reloadedBusySlot = appointmentSlotRepository.findById(busyDoctorSlot.getId()).orElseThrow();
        assertThat(reloadedBusySlot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    // Race condition thật qua tầng HTTP (không chỉ tầng service/repository như bước 3 và 7):
    // 2 request đồng thời tranh 1 slot duy nhất -> đúng 1 request thành công (201), request còn
    // lại phải nhận 409 (SlotFullyBookedException), không có double-booking hay lỗi 500.
    @Test
    void createAppointment_underConcurrency_onlyOneRequestSucceeds() throws Exception {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2027, 3, 2);
        LocalTime time = LocalTime.of(9, 0);
        AppointmentSlot slot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        String requestBody = """
                {"petId":"%s","date":"%s","startTime":"%s","reason":"Checkup"}
                """.formatted(UUID.randomUUID(), date, time);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(
                    () -> performBooking(requestBody, customerToken(UUID.randomUUID())), pool);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(
                    () -> performBooking(requestBody, customerToken(UUID.randomUUID())), pool);

            List<Integer> results = List.of(first.join(), second.join());

            assertThat(results).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }

        assertThat(appointmentRepository.findBySlotId(slot.getId())).isPresent();

        appointmentRepository.deleteAll();
        appointmentSlotRepository.deleteById(slot.getId());
    }

    // Thứ tự gợi ý (suggestedTimes) trong response 409 phải ưu tiên giờ gần nhất trước, không
    // phải thứ tự chèn hay thứ tự ngẫu nhiên trong DB.
    @Test
    @Transactional
    void createAppointment_slotFullyBooked_suggestedTimesOrderedByProximity() throws Exception {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        LocalDate date = LocalDate.of(2027, 3, 3);
        LocalTime requestedTime = LocalTime.of(10, 0);

        // Cố tình tạo theo thứ tự KHÔNG gần-nhất-trước để chứng minh service tự sort lại, không
        // phụ thuộc thứ tự insert.
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date)
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(13, 30)).build());
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date)
                .startTime(LocalTime.of(9, 30)).endTime(LocalTime.of(10, 0)).build());
        appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(date)
                .startTime(LocalTime.of(10, 30)).endTime(LocalTime.of(11, 0)).build());

        mockMvc.perform(post("/booking/appointments")
                        .header("Authorization", "Bearer " + customerToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"petId":"%s","date":"%s","startTime":"%s","reason":"Checkup"}
                                """.formatted(UUID.randomUUID(), date, requestedTime)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.suggestedTimes.length()").value(3))
                .andExpect(jsonPath("$.suggestedTimes[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$.suggestedTimes[1].startTime").value("10:30:00"))
                .andExpect(jsonPath("$.suggestedTimes[2].startTime").value("13:00:00"));
    }

    private int performBooking(String requestBody, String token) {
        try {
            MvcResult result = mockMvc.perform(post("/booking/appointments")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn();
            return result.getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PetResponse dummyPet() {
        return new PetResponse(UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE",
                LocalDate.of(2020, 1, 1), null, Instant.now(), Instant.now());
    }
}
