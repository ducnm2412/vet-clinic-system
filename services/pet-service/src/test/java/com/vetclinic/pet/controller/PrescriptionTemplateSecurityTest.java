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

/** Đơn mẫu: bác sĩ và ADMIN dùng và quản lý; khách, quầy và người chưa đăng nhập bị chặn. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class PrescriptionTemplateSecurityTest {

    @Autowired private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

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

    private String bearer(String role) {
        return bearer(role, UUID.randomUUID());
    }

    private static UUID idOf(String json) {
        return UUID.fromString(json.replaceAll(".*?\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
    }

    private UUID givenMedication(String name) throws Exception {
        String body = mockMvc.perform(post("/medications").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"caution\":\"Khong dung cho meo\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private static String templateJson(String name, UUID medicationId) {
        return "{\"name\":\"" + name + "\",\"diagnosis\":\"Viem ruot\",\"items\":[{\"medicationId\":\""
                + medicationId + "\",\"dosage\":\"1 goi\",\"frequency\":\"2 lan/ngay\",\"durationDays\":3}]}";
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/prescription-templates")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/prescription-templates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"diagnosis\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customersAndStaffAreForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"name\":\"x\",\"diagnosis\":\"y\"}";
        for (String role : List.of("CUSTOMER", "STAFF")) {
            mockMvc.perform(get("/prescription-templates").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/prescription-templates/" + id).header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/prescription-templates").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/prescription-templates/" + id).header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/prescription-templates/" + id).header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void doctorAndAdminShareTemplatesAndCreatorComesFromToken() throws Exception {
        UUID medicationId = givenMedication("Tplsec Thuoc");
        UUID doctorId = UUID.randomUUID();

        String created = mockMvc.perform(post("/prescription-templates")
                        .header("Authorization", bearer("DOCTOR", doctorId))
                        .contentType(MediaType.APPLICATION_JSON).content(templateJson("Tplsec Tieu chay", medicationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(doctorId.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].medicationName").value("Tplsec Thuoc"))
                .andExpect(jsonPath("$.items[0].medicationCaution").value("Khong dung cho meo"))
                .andReturn().getResponse().getContentAsString();
        UUID id = idOf(created);

        // Mẫu dùng chung: ADMIN (người khác) thấy, sửa và xoá được mẫu của bác sĩ.
        mockMvc.perform(get("/prescription-templates?q=tplsec").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemCount").value(1));
        mockMvc.perform(put("/prescription-templates/" + id).header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(templateJson("Tplsec Tieu chay 2", medicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tplsec Tieu chay 2"))
                .andExpect(jsonPath("$.createdBy").value(doctorId.toString()));
        mockMvc.perform(delete("/prescription-templates/" + id).header("Authorization", bearer("DOCTOR")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/prescription-templates/" + id).header("Authorization", bearer("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        UUID medicationId = givenMedication("Tplsec Thuoc Loi");

        // Thiếu tên / chẩn đoán.
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \",\"diagnosis\":\"y\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest());
        // Dòng thuốc thiếu liều, thiếu medicationId, số ngày không hợp lệ.
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"diagnosis\":\"y\",\"items\":[{\"medicationId\":\"" + medicationId
                                + "\",\"frequency\":\"1\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"diagnosis\":\"y\",\"items\":[{\"dosage\":\"1\",\"frequency\":\"1\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"diagnosis\":\"y\",\"items\":[{\"medicationId\":\"" + medicationId
                                + "\",\"dosage\":\"1\",\"frequency\":\"1\",\"durationDays\":0}]}"))
                .andExpect(status().isBadRequest());
        // Thuốc không có trong danh mục.
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateJson("Tplsec Thuoc la", UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateNameIsConflictAndUnknownIdIsNotFound() throws Exception {
        UUID medicationId = givenMedication("Tplsec Thuoc Trung");
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(templateJson("Tplsec Trung ten", medicationId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/prescription-templates").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(templateJson("TPLSEC TRUNG TEN", medicationId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/prescription-templates/" + UUID.randomUUID()).header("Authorization", bearer("DOCTOR")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/prescription-templates/" + UUID.randomUUID()).header("Authorization", bearer("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(templateJson("Tplsec Khac", medicationId)))
                .andExpect(status().isNotFound());
    }
}
