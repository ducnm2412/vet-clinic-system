package com.vetclinic.booking.controller;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.messaging.AppointmentEventPublisher;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.support.TestJwtSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class AppointmentRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppointmentRepository appointmentRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockBean
    private PetServiceClient petServiceClient;
    @MockBean
    private ProfileServiceClient profileServiceClient;
    // Các yêu cầu ở đây commit thật (test đồng thời), nên thư báo sẽ lên RabbitMQ thật và
    // notification-service đang chạy sẽ gửi email. Chặn ở nguồn.
    @MockBean
    private AppointmentEventPublisher eventPublisher;

    // Lịch tạo qua HTTP được commit thật (cần cho test đồng thời), nên dọn tay sau mỗi test.
    private final LocalDate day = LocalDate.now(ClinicSchedule.ZONE_ID).plusDays(12);

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        when(petServiceClient.getMyPet(any(), any())).thenReturn(new PetResponse(UUID.randomUUID(),
                UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE", LocalDate.of(2020, 1, 1), null, null, null,
                Instant.now(), Instant.now()));
        when(profileServiceClient.listDoctors()).thenReturn(List.of(
                new DoctorSummaryResponse(UUID.randomUUID(), UUID.randomUUID(), "BS A", null, null, null)));
    }

    @AfterEach
    void cleanUp() {
        appointmentRepository.deleteAll();
    }

    @Test
    void customer_createsRequest_withoutAnyShift() throws Exception {
        mockMvc.perform(post("/booking/appointments/requests")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalTime.of(9, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_ASSIGNMENT"))
                .andExpect(jsonPath("$.slotId").doesNotExist())
                .andExpect(jsonPath("$.doctorUserId").doesNotExist())
                .andExpect(jsonPath("$.date").value(day.toString()))
                .andExpect(jsonPath("$.startTime").value("09:00:00"));
    }

    @Test
    void onlyCustomers_canSendRequests() throws Exception {
        String staff = TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("STAFF"));
        mockMvc.perform(post("/booking/appointments/requests")
                        .header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalTime.of(9, 0))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/booking/appointments/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalTime.of(9, 0))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTime_isBadRequest() throws Exception {
        mockMvc.perform(post("/booking/appointments/requests")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalTime.of(12, 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clinicHours_requiresLogin_andListsTheWorkingTimes() throws Exception {
        mockMvc.perform(get("/booking/clinic-hours").param("date", day.toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/booking/clinic-hours").param("date", day.toString())
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(ClinicSchedule.SLOT_TIMES.size()))
                .andExpect(jsonPath("$[0].startTime").value("08:00:00"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].remaining").value(1));
    }

    // Hai khách cùng gửi yêu cầu vào chỗ cuối cùng của một khung giờ: đúng một người được nhận.
    // Không có khoá thì cả hai cùng đếm "còn 1 chỗ" rồi cùng được nhận → admin xếp không nổi.
    @Test
    void concurrentRequests_forTheLastSpot_onlyOneSucceeds() throws Exception {
        String a = customerToken();
        String b = customerToken();
        String body = body(LocalTime.of(10, 0));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> send(a, body), pool);
            CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> send(b, body), pool);
            assertThat(List.of(first.join(), second.join())).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
    }

    private int send(String token, String body) {
        try {
            return mockMvc.perform(post("/booking/appointments/requests")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String body(LocalTime time) {
        return """
                {"petId":"%s","date":"%s","startTime":"%s","reason":"Ngứa tai"}
                """.formatted(UUID.randomUUID(), day, time);
    }

    private String customerToken() {
        return TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("CUSTOMER"));
    }
}
