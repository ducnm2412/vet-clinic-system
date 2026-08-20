package com.vetclinic.auth.controller;

import com.vetclinic.auth.security.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void createStaffAccount_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createStaffAccount_withCustomerToken_returns403() throws Exception {
        String customerToken = jwtUtil.generateAccessToken("customer@example.com", List.of("CUSTOMER"));

        String body = """
                {"firstName":"X","lastName":"Y","email":"blocked@example.com",
                 "password":"password123","role":"DOCTOR"}
                """;

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createStaffAccount_withAdminToken_createsActiveDoctorAccount() throws Exception {
        String adminToken = jwtUtil.generateAccessToken("admin-caller@example.com", List.of("ADMIN"));

        String body = """
                {"firstName":"House","lastName":"MD","email":"it-doctor@example.com",
                 "password":"password123","role":"DOCTOR"}
                """;

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created for it-doctor@example.com with role DOCTOR"));

        // Tài khoản do Admin tạo phải ACTIVE ngay — login được luôn, không cần verify email.
        String loginBody = """
                {"email":"it-doctor@example.com","password":"password123"}
                """;

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void createStaffAccount_withCustomerRoleInBody_returns400() throws Exception {
        String adminToken = jwtUtil.generateAccessToken("admin-caller2@example.com", List.of("ADMIN"));

        String body = """
                {"firstName":"Sneaky","lastName":"User","email":"it-sneaky@example.com",
                 "password":"password123","role":"CUSTOMER"}
                """;

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
