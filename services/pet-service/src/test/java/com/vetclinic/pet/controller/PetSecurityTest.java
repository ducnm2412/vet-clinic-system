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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ai được làm gì với hồ sơ thú cưng: khách tự quản lý con của mình qua {@code /pets/me/**};
 * bác sĩ và nhân viên tra cứu mọi con qua {@code /pets/**} nhưng KHÔNG sửa được.
 *
 * Chủ nuôi luôn lấy từ token — không có đường nào để client tự khai mình là chủ của con khác.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class PetSecurityTest {

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

    private static final String MILO = """
            {"name":"Milo","species":"Chó","gender":"MALE","weightKg":5.5}
            """;

    @Test
    void anonymousIsRejectedEverywhere() throws Exception {
        mockMvc.perform(get("/pets/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/pets")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/pets/me").contentType(MediaType.APPLICATION_JSON).content(MILO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerManagesOwnPetsAndOwnerComesFromToken() throws Exception {
        UUID me = UUID.randomUUID();
        String customer = bearer("CUSTOMER", me);

        String created = mockMvc.perform(post("/pets/me").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(MILO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Milo"))
                .andExpect(jsonPath("$.ownerUserId").value(me.toString()))
                .andReturn().getResponse().getContentAsString();
        String petId = created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/pets/me").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Khách khác hỏi đúng id đó: không tìm thấy, không phải "không có quyền".
        mockMvc.perform(get("/pets/me/" + petId).header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void customersCannotBrowseTheWholeClinic() throws Exception {
        String customer = bearer("CUSTOMER");
        mockMvc.perform(get("/pets").header("Authorization", customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/pets/" + UUID.randomUUID()).header("Authorization", customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/pets/by-owners").header("Authorization", customer)
                        .param("ownerUserIds", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void clinicRolesLookUpButDoNotEdit() throws Exception {
        for (String role : List.of("DOCTOR", "STAFF", "ADMIN")) {
            mockMvc.perform(get("/pets").header("Authorization", bearer(role)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/pets/by-owners").header("Authorization", bearer(role))
                            .param("ownerUserIds", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            // Thêm hộ khách là việc của khách; vai trò phòng khám không có cửa nào vào /pets/me.
            mockMvc.perform(post("/pets/me").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(MILO))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void clinicStaffCreateAPetForACustomerAtTheCounter() throws Exception {
        UUID owner = UUID.randomUUID();
        String body = """
                {"ownerUserId":"%s","pet":{"name":"Mun","species":"Cho"}}
                """.formatted(owner);

        // CN-19: nhánh duy nhất nhận chủ nuôi từ thân request — chỉ nhân viên phòng khám gọi được.
        mockMvc.perform(post("/pets").header("Authorization", bearer("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").value(owner.toString()))
                .andExpect(jsonPath("$.name").value("Mun"));

        // Con vật vừa lập thuộc về khách đó, không thuộc nhân viên vừa bấm nút.
        mockMvc.perform(get("/pets/me").header("Authorization", bearer("CUSTOMER", owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        for (String role : List.of("CUSTOMER", "DOCTOR")) {
            mockMvc.perform(post("/pets").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidBodyIsRejectedBeforeItReachesTheDatabase() throws Exception {
        String customer = bearer("CUSTOMER");

        mockMvc.perform(post("/pets/me").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"","species":"Chó"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/pets/me").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Nặng âm","species":"Chó","weightKg":-1}
                                """))
                .andExpect(status().isBadRequest());
    }
}
