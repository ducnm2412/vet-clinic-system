package com.vetclinic.profile.controller;

import com.vetclinic.profile.domain.Address;
import com.vetclinic.profile.domain.CustomerProfile;
import com.vetclinic.profile.repository.CustomerProfileRepository;
import com.vetclinic.profile.support.TestJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trang Khách hàng của admin. Transaction rollback, chạy trên profile_db_test (VD-12). */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CustomerSummaryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerProfileRepository customerProfileRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String bearer(String role) {
        return "Bearer " + TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of(role));
    }

    private UUID customerWithAddresses() {
        UUID userId = UUID.randomUUID();
        CustomerProfile profile = CustomerProfile.builder().userId(userId).phone("0901234567").build();
        profile.getAddresses().add(Address.builder().customerProfile(profile).line1("12 Lê Lợi").city("Huế").build());
        profile.getAddresses().add(Address.builder().customerProfile(profile).line1("5 Hai Bà Trưng")
                .ward("Phường 6").city("TP.HCM").isDefault(true).build());
        customerProfileRepository.saveAndFlush(profile);
        return userId;
    }

    @Test
    void adminGetsPhoneAndDefaultAddressForRequestedUsersOnly() throws Exception {
        UUID withProfile = customerWithAddresses();
        UUID noProfile = UUID.randomUUID();

        mockMvc.perform(get("/profile/customers/summary").param("userIds", withProfile + "," + noProfile)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(withProfile.toString()))
                .andExpect(jsonPath("$[0].phone").value("0901234567"))
                .andExpect(jsonPath("$[0].address").value("5 Hai Bà Trưng, Phường 6, TP.HCM"));
    }

    @Test
    void onlyAdminMaySeeOtherCustomersContactDetails() throws Exception {
        String ids = UUID.randomUUID().toString();
        mockMvc.perform(get("/profile/customers/summary").param("userIds", ids)).andExpect(status().isUnauthorized());
        for (String role : List.of("CUSTOMER", "STAFF", "DOCTOR")) {
            mockMvc.perform(get("/profile/customers/summary").param("userIds", ids).header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsMoreThanOneHundredIds() throws Exception {
        String ids = Collections.nCopies(101, 0).stream().map(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));
        mockMvc.perform(get("/profile/customers/summary").param("userIds", ids).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
    }
}
