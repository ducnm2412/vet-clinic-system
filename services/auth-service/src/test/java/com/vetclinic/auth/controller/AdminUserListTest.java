package com.vetclinic.auth.controller;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VD-18: GET /admin/users. Chạy trong transaction và rollback — nhưng vẫn chạm database thật,
 * nên chạy với DB_NAME=auth_db_test như các test khác của service (xem VD-12).
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AdminUserListTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private String adminToken;

    @BeforeEach
    void seed() {
        adminToken = "Bearer " + jwtUtil.generateAccessToken(UUID.randomUUID(), "list-admin@example.com", List.of("ADMIN"));
        save("Nguyễn Thị", "Lan", "lan.list@example.com", UserStatus.ACTIVE, RoleName.CUSTOMER);
        save("Trần", "Bình", "binh.list@example.com", UserStatus.INACTIVE, RoleName.CUSTOMER);
        save("Lê", "Quản Lý", "ql.list@example.com", UserStatus.ACTIVE, RoleName.STAFF, RoleName.DOCTOR);
        save("Phạm", "Gạch_Dưới", "under_score.list@example.com", UserStatus.ACTIVE, RoleName.STAFF);
    }

    private void save(String first, String last, String email, UserStatus status, RoleName... roles) {
        Set<com.vetclinic.auth.domain.Role> set = new HashSet<>();
        for (RoleName r : roles) {
            set.add(roleRepository.findByName(r).orElseThrow());
        }
        userRepository.save(User.builder().firstName(first).lastName(last).email(email)
                .passwordHash("not-a-real-hash").status(status).roles(set).build());
    }

    @Test
    void onlyAdminCanList() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "STAFF", "DOCTOR")) {
            String token = jwtUtil.generateAccessToken(UUID.randomUUID(), role + "@example.com", List.of(role));
            mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void filtersByRoleAndStatusWithoutLeakingSecrets() throws Exception {
        mockMvc.perform(get("/admin/users").param("role", "CUSTOMER").param("keyword", ".list@")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].roles[*]", not(hasItem("STAFF"))))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].verificationToken").doesNotExist());

        mockMvc.perform(get("/admin/users").param("role", "CUSTOMER").param("status", "INACTIVE")
                        .param("keyword", ".list@").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("binh.list@example.com"));
    }

    @Test
    void userWithTwoRolesAppearsOnce() throws Exception {
        mockMvc.perform(get("/admin/users").param("keyword", "ql.list").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roles.length()").value(2));
    }

    @Test
    void keywordMatchesFullNameIgnoringCaseAndTreatsUnderscoreLiterally() throws Exception {
        mockMvc.perform(get("/admin/users").param("keyword", "NGUYỄN THỊ LAN").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("lan.list@example.com"));

        // "_" được tìm đúng ký tự, không phải ký tự đại diện: "q_.list" không được khớp "ql.list".
        mockMvc.perform(get("/admin/users").param("keyword", "r_score.list").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/admin/users").param("keyword", "q_.list").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));
        // "%" cũng vậy — không escape thì nó khớp mọi tài khoản.
        mockMvc.perform(get("/admin/users").param("keyword", "%").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pageSizeIsCappedAndBadEnumIs400() throws Exception {
        mockMvc.perform(get("/admin/users").param("size", "5000").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/admin/users").param("role", "SUPERUSER").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }
}
