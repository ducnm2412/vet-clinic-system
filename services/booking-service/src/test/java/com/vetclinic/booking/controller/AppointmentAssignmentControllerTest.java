package com.vetclinic.booking.controller;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.service.AppointmentRequestService;
import com.vetclinic.booking.support.TestJwtSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AppointmentAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppointmentRequestService requests;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @MockBean
    private PetServiceClient petServiceClient;
    @MockBean
    private ProfileServiceClient profileServiceClient;

    private final LocalDate day = LocalDate.now(ClinicSchedule.ZONE_ID).plusDays(15);
    private final UUID doctor = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();
    private AppointmentResponse request;

    @BeforeEach
    void setUp() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(new PetResponse(UUID.randomUUID(),
                UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE", LocalDate.of(2020, 1, 1), null, null, null,
                Instant.now(), Instant.now()));
        when(profileServiceClient.listDoctors()).thenReturn(List.of(
                new DoctorSummaryResponse(UUID.randomUUID(), doctor, "BS An", null, null, null)));
        request = requests.createRequest(customer, "Bearer t",
                new AppointmentRequest(UUID.randomUUID(), day, LocalTime.of(9, 0), null, null));
    }

    @Test
    void adminEndpoints_areForbiddenForEveryoneElse() throws Exception {
        for (String role : List.of("CUSTOMER", "STAFF", "DOCTOR")) {
            String token = token(UUID.randomUUID(), role);
            mockMvc.perform(get("/booking/appointments/awaiting").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/booking/appointments/{id}/available-doctors", request.id())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/booking/appointments/{id}/assign", request.id())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"doctorUserId\":\"%s\"}".formatted(doctor)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/booking/appointments/awaiting")).andExpect(status().isUnauthorized());
    }

    @Test
    void admin_assigns_andTheDoctorSeesTheAppointment() throws Exception {
        String admin = token(UUID.randomUUID(), "ADMIN");

        mockMvc.perform(get("/booking/appointments/awaiting").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='%s')].status".formatted(request.id())).value("AWAITING_ASSIGNMENT"));

        mockMvc.perform(get("/booking/appointments/{id}/available-doctors", request.id())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doctorUserId").value(doctor.toString()))
                .andExpect(jsonPath("$[0].free").value(true));

        mockMvc.perform(put("/booking/appointments/{id}/assign", request.id())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorUserId\":\"%s\"}".formatted(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.doctorUserId").value(doctor.toString()));

        mockMvc.perform(get("/booking/appointments/doctor/me").param("date", day.toString())
                        .header("Authorization", "Bearer " + token(doctor, "DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(request.id())).exists());
    }

    @Test
    void proposal_isHiddenFromTheDoctorUntilTheCustomerAccepts() throws Exception {
        String admin = token(UUID.randomUUID(), "ADMIN");
        mockMvc.perform(put("/booking/appointments/{id}/propose", request.id())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorUserId\":\"%s\",\"date\":\"%s\",\"startTime\":\"10:00\"}"
                                .formatted(doctor, day)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CUSTOMER"));

        String doctorToken = token(doctor, "DOCTOR");
        mockMvc.perform(get("/booking/appointments/doctor/me").param("date", day.toString())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(request.id())).doesNotExist());

        // Chỉ chủ lịch đồng ý được; người khác nhận 404, admin nhận 403.
        mockMvc.perform(put("/booking/appointments/{id}/accept-proposal", request.id())
                        .header("Authorization", "Bearer " + token(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/booking/appointments/{id}/accept-proposal", request.id())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/booking/appointments/{id}/accept-proposal", request.id())
                        .header("Authorization", "Bearer " + token(customer, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/booking/appointments/doctor/me").param("date", day.toString())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(request.id())).exists());
    }

    @Test
    void assigningAnAlreadyHandledRequest_isConflict() throws Exception {
        String admin = token(UUID.randomUUID(), "ADMIN");
        String body = "{\"doctorUserId\":\"%s\"}".formatted(doctor);
        mockMvc.perform(put("/booking/appointments/{id}/assign", request.id())
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/booking/appointments/{id}/assign", request.id())
                .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    private String token(UUID userId, String role) {
        return TestJwtSupport.token(jwtSecret, userId, List.of(role));
    }
}
