package com.vetclinic.reporting.controller;

import com.vetclinic.reporting.client.SourceClients.BookingStatsClient;
import com.vetclinic.reporting.client.SourceClients.OrderStatsClient;
import com.vetclinic.reporting.client.SourceClients.PaymentStatsClient;
import com.vetclinic.reporting.client.SourceClients.ProfileClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Không cần database hay Eureka: service này không có database, nguồn số liệu là mock. */
@SpringBootTest(properties = {
        "jwt.secret=" + ReportingControllerTest.SECRET,
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
class ReportingControllerTest {

    static final String SECRET = "reporting-test-secret-that-is-long-enough-for-hs256";

    @Autowired private MockMvc mvc;

    @MockBean private OrderStatsClient orderStats;
    @MockBean private PaymentStatsClient paymentStats;
    @MockBean private BookingStatsClient bookingStats;
    @MockBean private ProfileClient profile;

    private static String bearer(String role) {
        String token = Jwts.builder()
                .subject(role.toLowerCase() + "@vetclinic.vn")
                .claim("userId", UUID.randomUUID().toString())
                .claim("roles", List.of(role))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/reporting/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCustomerAndDoctorCannotSeeRevenue() throws Exception {
        for (String role : List.of("STAFF", "CUSTOMER", "DOCTOR")) {
            mvc.perform(get("/reporting/revenue").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminGetsLast30DaysByDefaultAndTokenIsForwarded() throws Exception {
        String token = bearer("ADMIN");
        when(orderStats.daily(any(), any(), any())).thenReturn(List.of());
        when(paymentStats.daily(any(), any(), any())).thenReturn(List.of());

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        mvc.perform(get("/reporting/revenue").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interval").value("DAY"))
                .andExpect(jsonPath("$.points.length()").value(30))
                .andExpect(jsonPath("$.totals.total").value(0))
                .andExpect(jsonPath("$.unavailable").isEmpty());

        // Service nguồn tự kiểm tra quyền lại, nên phải nhận đúng token của admin.
        verify(orderStats).daily(today.minusDays(29).toString(), today.toString(), token);
    }

    @Test
    void badParametersAre400WithReason() throws Exception {
        String token = bearer("ADMIN");
        mvc.perform(get("/reporting/revenue?from=2026-09-10&to=2026-09-01").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/reporting/revenue?interval=week").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingDownIs503NotServerError() throws Exception {
        when(bookingStats.stats(any(), any(), any())).thenThrow(new RuntimeException("down"));

        mvc.perform(get("/reporting/appointments").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isServiceUnavailable());
    }
}
