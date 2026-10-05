package com.vetclinic.staff.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ai được làm gì: xếp ca là việc của ADMIN; chấm công là việc của chính người đi làm; bảng công
 * của cả phòng khám chỉ ADMIN xem.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class StaffSecurityTest {

    @Autowired private MockMvc mockMvc;

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

    private String shiftBody() {
        return """
                {"userId":"%s","dates":["2026-10-05"],"startTime":"08:00:00","endTime":"12:00:00"}
                """.formatted(UUID.randomUUID());
    }

    @Test
    void anonymousIsRejectedEverywhere() throws Exception {
        mockMvc.perform(get("/staff/shifts").param("from", "2026-10-01").param("to", "2026-10-07"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/staff/attendance/check-in")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/staff/attendance/timesheet").param("from", "2026-10-01").param("to", "2026-10-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminSchedulesShifts() throws Exception {
        for (String role : List.of("STAFF", "DOCTOR", "CUSTOMER")) {
            mockMvc.perform(post("/staff/shifts").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(shiftBody()))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/staff/shifts").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(shiftBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created.length()").value(1));
    }

    @Test
    void customersHaveNoBusinessWithAttendance() throws Exception {
        String customer = bearer("CUSTOMER");
        mockMvc.perform(post("/staff/attendance/check-in").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/staff/attendance/me").header("Authorization", customer)
                        .param("from", "2026-10-01").param("to", "2026-10-07"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/staff/shifts").header("Authorization", customer)
                        .param("from", "2026-10-01").param("to", "2026-10-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffChecksInForThemselvesOnly() throws Exception {
        UUID myUserId = UUID.randomUUID();
        String me = bearer("STAFF", myUserId);

        mockMvc.perform(post("/staff/attendance/check-in").header("Authorization", me))
                .andExpect(status().isOk())
                // userId lấy từ token, client không gửi lên được.
                .andExpect(jsonPath("$.userId").value(myUserId.toString()))
                .andExpect(jsonPath("$.date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.checkOutAt").doesNotExist());

        mockMvc.perform(post("/staff/attendance/check-out").header("Authorization", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkOutAt").isNotEmpty());
    }

    @Test
    void timesheetOfEveryoneIsAdminOnly() throws Exception {
        for (String role : List.of("STAFF", "DOCTOR")) {
            mockMvc.perform(get("/staff/attendance/timesheet").header("Authorization", bearer(role))
                            .param("from", "2026-10-01").param("to", "2026-10-07"))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/staff/attendance/timesheet").header("Authorization", bearer("ADMIN"))
                        .param("from", "2026-10-01").param("to", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray());
    }
}
