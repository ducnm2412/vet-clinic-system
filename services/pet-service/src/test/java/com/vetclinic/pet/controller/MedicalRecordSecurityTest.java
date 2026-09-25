package com.vetclinic.pet.controller;

import com.vetclinic.pet.client.BookingServiceClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ai được làm gì với bệnh án: bác sĩ lập, quầy giao thuốc, khách chỉ đọc của mình.
 *
 * Hai nhánh phải chặn được người ngoài kể cả khi biết đúng UUID lịch hẹn.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class MedicalRecordSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private BookingServiceClient bookingServiceClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String bearer(String role) {
        return bearer(role, UUID.randomUUID());
    }

    private String bearer(String role, UUID userId) {
        String token = Jwts.builder()
                .subject(role.toLowerCase() + "@vetclinic.vn")
                .claim("userId", userId.toString())
                .claim("roles", List.of(role))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    private static final String RECORD = """
            {"diagnosis":"Viem da","treatment":"Uong thuoc","prescriptionItems":[
              {"medicationName":"Amoxicillin","dosage":"1 vien","frequency":"2 lan/ngay","durationDays":5}]}
            """;

    /** Tạo một con vật của khách rồi trả về id — dùng mock lịch hẹn trỏ vào con đó. */
    private UUID givenPetOf(UUID ownerUserId) throws Exception {
        String body = mockMvc.perform(post("/pets/me").header("Authorization", bearer("CUSTOMER", ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Milo\",\"species\":\"Cho\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
    }

    @Test
    void anonymousIsRejectedEverywhere() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        mockMvc.perform(get("/medical-records/by-appointment/" + appointmentId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/medical-records/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/medical-records/by-appointment/" + appointmentId)
                        .contentType(MediaType.APPLICATION_JSON).content(RECORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyDoctorsWriteRecords() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        for (String role : List.of("CUSTOMER", "STAFF", "ADMIN")) {
            mockMvc.perform(put("/medical-records/by-appointment/" + appointmentId)
                            .header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(RECORD))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void doctorWritesAndCustomerReadsOnlyTheirOwn() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        UUID petId = givenPetOf(owner);
        UUID appointmentId = UUID.randomUUID();
        when(bookingServiceClient.getAppointment(any(), any()))
                .thenReturn(new BookingServiceClient.AppointmentRef(appointmentId, petId, owner, doctor, "COMPLETED"));

        mockMvc.perform(put("/medical-records/by-appointment/" + appointmentId)
                        .header("Authorization", bearer("DOCTOR", doctor))
                        .contentType(MediaType.APPLICATION_JSON).content(RECORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.petId").value(petId.toString()))
                .andExpect(jsonPath("$.prescriptionItems.length()").value(1));

        mockMvc.perform(get("/medical-records/by-appointment/" + appointmentId)
                        .header("Authorization", bearer("CUSTOMER", owner)))
                .andExpect(status().isOk());

        // Khách khác biết đúng id lịch hẹn vẫn không đọc được.
        mockMvc.perform(get("/medical-records/by-appointment/" + appointmentId)
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isNotFound());

        // Chưa thanh toán thì quầy không giao thuốc được.
        mockMvc.perform(put("/medical-records/by-appointment/" + appointmentId + "/receive")
                        .header("Authorization", bearer("STAFF")))
                .andExpect(status().isConflict());
    }

    @Test
    void emptyMedicationNameIsRejectedBeforeTheDatabase() throws Exception {
        mockMvc.perform(put("/medical-records/by-appointment/" + UUID.randomUUID())
                        .header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagnosis":"Viem da","prescriptionItems":[
                                  {"medicationName":"","dosage":"1 vien","frequency":"2 lan/ngay"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queuesAreScopedToTheRightRole() throws Exception {
        mockMvc.perform(get("/medical-records/pending").header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/medical-records/pending").header("Authorization", bearer("DOCTOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/medical-records/mine/outstanding").header("Authorization", bearer("DOCTOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/medical-records/mine/outstanding").header("Authorization", bearer("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void petHistoryFollowsTheSameOwnershipRule() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID petId = givenPetOf(owner);

        mockMvc.perform(get("/pets/me/" + petId + "/medical-records")
                        .header("Authorization", bearer("CUSTOMER", owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/pets/me/" + petId + "/medical-records")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/pets/" + petId + "/medical-records").header("Authorization", bearer("DOCTOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pets/" + petId + "/medical-records").header("Authorization", bearer("CUSTOMER", owner)))
                .andExpect(status().isForbidden());
    }
}
