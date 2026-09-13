package com.vetclinic.auth.controller;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.messaging.UserStatusChangedEvent;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.jwt.JwtUtil;
import com.vetclinic.auth.service.AuthService;
import com.vetclinic.auth.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CN-08 / VD-06. Transaction rollback, chạy trên auth_db_test (xem VD-12). */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class AccountLockTest {

    private static final String PASSWORD = "password123";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;
    @Autowired private ApplicationEvents events;

    private String adminToken;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        adminToken = "Bearer " + jwtUtil.generateAccessToken(adminId, "lock-admin@example.com", List.of("ADMIN"));
    }

    private User save(String email, UserStatus status, String verificationToken, RoleName role) {
        return userRepository.save(User.builder().firstName("Khoa").lastName("Test").email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD)).status(status).verificationToken(verificationToken)
                .roles(new java.util.HashSet<>(Set.of(roleRepository.findByName(role).orElseThrow()))).build());
    }

    private String loginBody(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    @Test
    void lockRevokesSessionsBlocksLoginAndTellsBookingService() throws Exception {
        User doctor = save("doctor.lock@example.com", UserStatus.ACTIVE, null, RoleName.DOCTOR);
        String refreshToken = authService.login(new LoginRequest(doctor.getEmail(), PASSWORD)).refreshToken();

        mockMvc.perform(put("/admin/users/{id}/lock", doctor.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // Phiên đang mở không làm mới được nữa.
        assertThat(refreshTokenRepository.findAll()).filteredOn(t -> t.getUserId().equals(doctor.getId()))
                .allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        // Đúng mật khẩu thì được biết là bị khoá (403), không phải "sai mật khẩu".
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(doctor.getEmail())))
                .andExpect(status().isForbidden());

        assertThat(events.stream(UserStatusChangedEvent.class))
                .containsExactly(new UserStatusChangedEvent(doctor.getId(), List.of("DOCTOR"), true));
    }

    @Test
    void wrongPasswordOnLockedAccountStillLooksLikeWrongPassword() throws Exception {
        User user = save("quiet.lock@example.com", UserStatus.LOCKED, null, RoleName.CUSTOMER);
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unlockRestoresLoginButNotSkippedEmailVerification() throws Exception {
        User verified = save("verified.lock@example.com", UserStatus.LOCKED, null, RoleName.CUSTOMER);
        User unverified = save("unverified.lock@example.com", UserStatus.LOCKED, "pending-token", RoleName.CUSTOMER);

        mockMvc.perform(put("/admin/users/{id}/unlock", verified.getId()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(put("/admin/users/{id}/unlock", unverified.getId()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(verified.getEmail())))
                .andExpect(status().isOk());
        assertThat(events.stream(UserStatusChangedEvent.class)).extracting(UserStatusChangedEvent::locked)
                .containsExactly(false, false);
    }

    @Test
    void adminCannotLockThemselfOrTheLastActiveAdmin() throws Exception {
        User me = save("me.lock@example.com", UserStatus.ACTIVE, null, RoleName.ADMIN);
        String myToken = "Bearer " + jwtUtil.generateAccessToken(me.getId(), me.getEmail(), List.of("ADMIN"));
        mockMvc.perform(put("/admin/users/{id}/lock", me.getId()).header("Authorization", myToken))
                .andExpect(status().isConflict());

        // Chỉ còn đúng một admin hoạt động là "me" thì người khác cũng không khoá được.
        userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN))
                .forEach(u -> u.setStatus(UserStatus.LOCKED));
        userRepository.flush();
        mockMvc.perform(put("/admin/users/{id}/lock", me.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict());
        assertThat(events.stream(UserStatusChangedEvent.class)).isEmpty();
    }

    @Test
    void onlyAdminCanLock() throws Exception {
        User user = save("target.lock@example.com", UserStatus.ACTIVE, null, RoleName.CUSTOMER);
        String staffToken = "Bearer " + jwtUtil.generateAccessToken(UUID.randomUUID(), "s@example.com", List.of("STAFF"));
        mockMvc.perform(put("/admin/users/{id}/lock", user.getId()).header("Authorization", staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/users/{id}/lock", UUID.randomUUID()).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }
}
