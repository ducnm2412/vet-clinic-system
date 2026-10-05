package com.vetclinic.pet.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ai được làm gì với hồ sơ thú cưng: khách tự quản lý con của mình qua {@code /pets/me/**};
 * bác sĩ và nhân viên tra cứu mọi con qua {@code /pets/**} nhưng KHÔNG sửa được.
 *
 * Chủ nuôi luôn lấy từ token — không có đường nào để client tự khai mình là chủ của con khác.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "app.upload.dir=${java.io.tmpdir}/pet-photo-test"})
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

    // 8 byte chữ ký PNG + vài byte dữ liệu: đủ để qua kiểm tra nội dung thật của file.
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    private String createMilo(String customer) throws Exception {
        String created = mockMvc.perform(post("/pets/me").header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(MILO))
                .andReturn().getResponse().getContentAsString();
        return created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void ownerUploadsPhotoAndOnlyOwnerAndClinicCanReadIt() throws Exception {
        UUID me = UUID.randomUUID();
        String customer = bearer("CUSTOMER", me);
        String petId = createMilo(customer);

        mockMvc.perform(get("/pets/me/" + petId).header("Authorization", customer))
                .andExpect(jsonPath("$.photoUrl").doesNotExist());
        mockMvc.perform(get("/pets/photos/" + petId).header("Authorization", customer))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart("/pets/me/" + petId + "/photo")
                        .file(new MockMultipartFile("file", "milo.png", "image/png", PNG))
                        .header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("/pets/photos/" + petId + "?v=1"));

        mockMvc.perform(get("/pets/photos/" + petId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));
        for (String role : List.of("DOCTOR", "STAFF", "ADMIN")) {
            mockMvc.perform(get("/pets/photos/" + petId).header("Authorization", bearer(role)))
                    .andExpect(status().isOk());
        }

        // Khách khác: không thấy ảnh, không phải "không có quyền".
        mockMvc.perform(get("/pets/photos/" + petId).header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/pets/photos/" + petId)).andExpect(status().isUnauthorized());

        // Tải ảnh mới: tăng phiên bản để trình duyệt bỏ cache.
        mockMvc.perform(multipart("/pets/me/" + petId + "/photo")
                        .file(new MockMultipartFile("file", "milo.png", "image/png", PNG))
                        .header("Authorization", customer))
                .andExpect(jsonPath("$.photoUrl").value("/pets/photos/" + petId + "?v=2"));
    }

    @Test
    void photoUploadRejectsNonImagesAndOtherPeoplesPets() throws Exception {
        String customer = bearer("CUSTOMER");
        String petId = createMilo(customer);

        // Tên và Content-Type khai là ảnh nhưng nội dung không phải: bị từ chối theo nội dung.
        mockMvc.perform(multipart("/pets/me/" + petId + "/photo")
                        .file(new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes()))
                        .header("Authorization", customer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/pets/me/" + petId + "/photo")
                        .file(new MockMultipartFile("file", "milo.png", "image/png", PNG))
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/pets/me/" + petId + "/photo")
                        .file(new MockMultipartFile("file", "milo.png", "image/png", PNG))
                        .header("Authorization", bearer("STAFF")))
                .andExpect(status().isForbidden());
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
