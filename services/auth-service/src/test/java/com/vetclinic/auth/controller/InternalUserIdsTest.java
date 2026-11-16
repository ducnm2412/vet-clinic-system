package com.vetclinic.auth.controller;

import com.vetclinic.auth.domain.Role;
import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /internal/users/ids: notification-service lấy id cả nhóm vai trò để gửi thông báo. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "app.internal-api-key=test-internal-key"})
@AutoConfigureMockMvc
@Transactional
class InternalUserIdsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private UUID save(String email, RoleName role, UserStatus status) {
        Role r = roleRepository.findByName(role).orElseThrow();
        return userRepository.save(User.builder().firstName("A").lastName("B").email(email)
                .passwordHash("not-a-real-hash").status(status).roles(Set.of(r)).build()).getId();
    }

    @Test
    void returnsOnlyActiveUsersOfThatRole() throws Exception {
        UUID active = save("internal.staff1@example.com", RoleName.STAFF, UserStatus.ACTIVE);
        UUID locked = save("internal.staff2@example.com", RoleName.STAFF, UserStatus.LOCKED);
        UUID doctor = save("internal.doctor@example.com", RoleName.DOCTOR, UserStatus.ACTIVE);

        mockMvc.perform(get("/internal/users/ids").param("role", "STAFF").header("X-Internal-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(active.toString())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(locked.toString()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(doctor.toString()))));
    }

    @Test
    void rejectsMissingOrWrongKey() throws Exception {
        mockMvc.perform(get("/internal/users/ids").param("role", "STAFF"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/users/ids").param("role", "STAFF").header("X-Internal-Key", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminJwtAloneIsNotEnough() throws Exception {
        String admin = "Bearer " + jwtUtil.generateAccessToken(UUID.randomUUID(), "admin@vetclinic.vn", List.of("ADMIN"));
        mockMvc.perform(get("/internal/users/ids").param("role", "STAFF").header("Authorization", admin))
                .andExpect(status().isForbidden());
    }
}
