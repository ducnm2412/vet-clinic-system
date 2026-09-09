package com.vetclinic.booking.client;

import com.vetclinic.booking.dto.PetResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// KHÔNG tắt eureka.client.enabled — test này cần đăng ký thật vào Eureka để Feign LoadBalancer
// resolve được tên "profile-service" ra instance thật đang chạy (bắt buộc profile-service phải
// đang chạy sẵn cùng Eureka server thật khi chạy test này).
@SpringBootTest
class ProfileServiceClientTest {

    private static final UUID TEST_PET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TEST_CUSTOMER_USER_ID = UUID.fromString("9a83e065-8ded-4b6c-8fc7-33880c181df4");

    @Autowired
    private ProfileServiceClient profileServiceClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void getPetById_fetchesRealPetFromProfileServiceOverEureka() {
        String token = "Bearer " + signToken(TEST_CUSTOMER_USER_ID, "STAFF");

        PetResponse pet = profileServiceClient.getPetById(TEST_PET_ID, token);

        assertThat(pet.name()).isEqualTo("Milo");
        assertThat(pet.species()).isEqualTo("Dog");
    }

    @Test
    void getMyPet_asOwningCustomer_fetchesOwnPet() {
        String token = "Bearer " + signToken(TEST_CUSTOMER_USER_ID, "CUSTOMER");

        PetResponse pet = profileServiceClient.getMyPet(TEST_PET_ID, token);

        assertThat(pet.name()).isEqualTo("Milo");
    }

    private String signToken(UUID userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("test@vetclinic.com")
                .claim("userId", userId.toString())
                .claim("roles", List.of(role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }
}
