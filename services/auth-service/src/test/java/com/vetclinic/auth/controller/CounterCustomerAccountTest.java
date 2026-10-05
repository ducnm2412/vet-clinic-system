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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CN-19: lễ tân mở tài khoản cho khách vãng lai ngay tại quầy.
 *
 * Điểm khác đăng ký online phải giữ được: tài khoản dùng ngay, không chờ bấm link trong email.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CounterCustomerAccountTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    private String token(String role) {
        return "Bearer " + jwtUtil.generateAccessToken(UUID.randomUUID(), role.toLowerCase() + "@vetclinic.vn",
                List.of(role));
    }

    private String body(String email) {
        return """
                {"firstName":"Tran","lastName":"Minh","email":"%s","password":"matkhau123","phone":"0912345678"}
                """.formatted(email);
    }

    @Test
    void staffOpensAnAccountThatWorksImmediately() throws Exception {
        mockMvc.perform(post("/auth/customers").header("Authorization", token("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("quay1@vetclinic.vn")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("quay1@vetclinic.vn"))
                // Người thật đang đứng ở quầy — không bắt họ mở hộp thư để bấm link xác minh.
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void sameEmailTwiceIsRefused() throws Exception {
        mockMvc.perform(post("/auth/customers").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("quay2@vetclinic.vn")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/customers").header("Authorization", token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("quay2@vetclinic.vn")))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyClinicStaffOpenAccountsForOthers() throws Exception {
        mockMvc.perform(post("/auth/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(body("x@vetclinic.vn")))
                .andExpect(status().isUnauthorized());

        for (String role : List.of("CUSTOMER", "DOCTOR")) {
            mockMvc.perform(post("/auth/customers").header("Authorization", token(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body("y@vetclinic.vn")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void staffLooksUpCustomersButNotStaffAccounts() throws Exception {
        mockMvc.perform(post("/auth/customers").header("Authorization", token("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("timkiem@vetclinic.vn")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/auth/customers").header("Authorization", token("STAFF"))
                        .param("keyword", "timkiem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].roles[0]").value("CUSTOMER"));

        // Khách không tra được danh sách khách khác.
        mockMvc.perform(get("/auth/customers").header("Authorization", token("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void phoneMustLookLikeAPhoneNumber() throws Exception {
        mockMvc.perform(post("/auth/customers").header("Authorization", token("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Tran","lastName":"Minh","email":"quay3@vetclinic.vn",
                                 "password":"matkhau123","phone":"12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }
}
