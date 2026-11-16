package com.vetclinic.product.controller;

import com.vetclinic.product.support.TestJwtSupport;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kho thuốc: nhân viên và ADMIN dùng; bác sĩ, khách và người chưa đăng nhập bị chặn. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
@Transactional
class MedicationStockSecurityTest {

    @Autowired private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String bearer(String role) {
        return "Bearer " + TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of(role));
    }

    private String createMedication() throws Exception {
        String body = mockMvc.perform(post("/medications").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sec Kho Thuoc\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void anonymousDoctorsAndCustomersAreRejected() throws Exception {
        UUID id = UUID.randomUUID();
        String stock = "{\"type\":\"IMPORT\",\"quantityChange\":5}";
        mockMvc.perform(get("/medications/inventory")).andExpect(status().isUnauthorized());
        for (String role : List.of("DOCTOR", "CUSTOMER")) {
            mockMvc.perform(get("/medications/inventory").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/medications/" + id + "/stock").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(stock))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/medications/" + id + "/stock-movements").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/medications/" + id + "/low-stock-threshold").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"threshold\":3}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void staffAndAdminCanImportAndSeeStockAndHistory() throws Exception {
        String id = createMedication();
        for (String role : List.of("STAFF", "ADMIN")) {
            mockMvc.perform(post("/medications/" + id + "/stock").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"IMPORT\",\"quantityChange\":10,\"note\":\"Nhap hang\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/medications/inventory?q=sec kho").header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stockQuantity").value(20));
        mockMvc.perform(get("/medications/" + id + "/stock-movements").header("Authorization", bearer("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(put("/medications/" + id + "/low-stock-threshold").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"threshold\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockThreshold").value(8));
    }

    @Test
    void invalidStockRequestsAreRejected() throws Exception {
        String id = createMedication();
        // SALE chỉ do sự kiện phiếu thu sinh ra; không nhận qua API tay. Số 0 và âm sai kiểu cũng bị chặn.
        for (String body : List.of(
                "{\"type\":\"SALE\",\"quantityChange\":-1}",
                "{\"type\":\"IMPORT\",\"quantityChange\":0}",
                "{\"type\":\"IMPORT\",\"quantityChange\":-4}")) {
            mockMvc.perform(post("/medications/" + id + "/stock").header("Authorization", bearer("STAFF"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/medications/" + id + "/low-stock-threshold").header("Authorization", bearer("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"threshold\":-1}"))
                .andExpect(status().isBadRequest());
        // Điều chỉnh xuống dưới 0 là 409 (kho không đủ), không phải 500.
        mockMvc.perform(post("/medications/" + id + "/stock").header("Authorization", bearer("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ADJUSTMENT\",\"quantityChange\":-1}"))
                .andExpect(status().isConflict());
    }
}
