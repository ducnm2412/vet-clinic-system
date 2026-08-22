package com.vetclinic.profile.controller;

import com.vetclinic.profile.support.TestJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class StaffProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String staffToken() {
        return TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("STAFF"));
    }

    private String adminToken() {
        return TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("ADMIN"));
    }

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/profile/staff/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withDoctorToken_returns403() throws Exception {
        String doctorToken = TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(get("/profile/staff/me").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void putMe_withStaffToken_updatesProfile() throws Exception {
        mockMvc.perform(put("/profile/staff/me")
                        .header("Authorization", "Bearer " + staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"position":"Le tan","phone":"0922222222","hireDate":"2023-03-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("Le tan"));
    }

    @Test
    void putMe_missingPosition_returns400() throws Exception {
        mockMvc.perform(put("/profile/staff/me")
                        .header("Authorization", "Bearer " + staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"0900000000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.position").exists());
    }

    @Test
    void listStaff_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/profile/staff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listStaff_withStaffToken_returns403() throws Exception {
        mockMvc.perform(get("/profile/staff").header("Authorization", "Bearer " + staffToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listStaff_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/profile/staff").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }
}
