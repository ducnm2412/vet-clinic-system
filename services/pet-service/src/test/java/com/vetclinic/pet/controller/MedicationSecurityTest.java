package com.vetclinic.pet.controller;

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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Danh mục thuốc: bác sĩ và ADMIN dùng và quản lý; khách, quầy và người chưa đăng nhập bị chặn. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class MedicationSecurityTest {

    @Autowired private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String bearer(String role) {
        String token = Jwts.builder()
                .subject(role.toLowerCase() + "@vetclinic.vn")
                .claim("userId", UUID.randomUUID().toString())
                .claim("roles", List.of(role))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    private static final String BODY = "{\"name\":\"Sec Test Thuoc\",\"unit\":\"vien\",\"caution\":\"Khong dung cho meo\"}";

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/medications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/medications").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customersAndStaffAreForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        for (String role : List.of("CUSTOMER", "STAFF")) {
            mockMvc.perform(get("/medications").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/medications").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/medications/" + id).header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/medications/" + id).header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void doctorAndAdminCanManageAndSearch() throws Exception {
        for (String role : List.of("DOCTOR", "ADMIN")) {
            String name = "Sec " + role + " Thuoc";
            String created = mockMvc.perform(post("/medications").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + name + "\",\"caution\":\"Khong dung cho meo\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.active").value(true))
                    .andReturn().getResponse().getContentAsString();
            UUID id = UUID.fromString(created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));

            mockMvc.perform(get("/medications?q=sec " + role.toLowerCase()).header("Authorization", bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].caution").value("Khong dung cho meo"));

            mockMvc.perform(delete("/medications/" + id).header("Authorization", bearer(role)))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void invalidAndDuplicateBodiesAreRejected() throws Exception {
        mockMvc.perform(post("/medications").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/medications").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/medications").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"SEC TEST THUOC\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        mockMvc.perform(put("/medications/" + UUID.randomUUID()).header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
}
