package com.vetclinic.auth.controller;

import com.vetclinic.auth.domain.Role;
import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /auth/customers/{userId}: nhân viên lấy họ tên một khách để điền hoá đơn quầy. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CustomerLookupTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private UUID customerId;
    private UUID doctorId;

    @BeforeEach
    void seed() {
        customerId = save("Nguyễn Thị", "Lan", "lookup.customer@example.com", RoleName.CUSTOMER);
        doctorId = save("Lê", "Bác Sĩ", "lookup.doctor@example.com", RoleName.DOCTOR);
    }

    private UUID save(String first, String last, String email, RoleName role) {
        Role r = roleRepository.findByName(role).orElseThrow();
        return userRepository.save(User.builder().firstName(first).lastName(last).email(email)
                .passwordHash("not-a-real-hash").status(UserStatus.ACTIVE).roles(Set.of(r)).build()).getId();
    }

    private String token(String role) {
        return "Bearer " + jwtUtil.generateAccessToken(UUID.randomUUID(), role.toLowerCase() + "@vetclinic.vn",
                List.of(role));
    }

    @Test
    void staffAndAdminGetTheCustomerName() throws Exception {
        for (String role : List.of("STAFF", "ADMIN")) {
            mockMvc.perform(get("/auth/customers/" + customerId).header("Authorization", token(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Nguyễn Thị"))
                    .andExpect(jsonPath("$.lastName").value("Lan"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
    }

    @Test
    void customersAndAnonymousCannotLookUpOthers() throws Exception {
        mockMvc.perform(get("/auth/customers/" + customerId)).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "DOCTOR")) {
            mockMvc.perform(get("/auth/customers/" + customerId).header("Authorization", token(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anAccountThatIsNotACustomerLooksLikeItDoesNotExist() throws Exception {
        mockMvc.perform(get("/auth/customers/" + doctorId).header("Authorization", token("STAFF")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/auth/customers/" + UUID.randomUUID()).header("Authorization", token("STAFF")))
                .andExpect(status().isNotFound());
    }
}
