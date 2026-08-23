package com.vetclinic.profile.controller;

import com.vetclinic.profile.support.TestJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CustomerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String customerToken() {
        return TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("CUSTOMER"));
    }

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/profile/customer/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withDoctorToken_returns403() throws Exception {
        String doctorToken = TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("DOCTOR"));

        mockMvc.perform(get("/profile/customer/me").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMe_thenPutMe_thenAddressAndPetFlow() throws Exception {
        String token = customerToken();

        mockMvc.perform(get("/profile/customer/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());

        mockMvc.perform(put("/profile/customer/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"0901234567","dateOfBirth":"1995-05-20"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0901234567"));

        mockMvc.perform(post("/profile/customer/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"line1":"123 Test St","city":"HCM","isDefault":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc.perform(get("/profile/customer/me/addresses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("HCM"));

        mockMvc.perform(post("/profile/customer/me/pets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Milo","species":"Dog"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Milo"));
    }

    @Test
    void postAddress_missingCity_returns400() throws Exception {
        mockMvc.perform(post("/profile/customer/me/addresses")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"line1":"no city"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.city").exists());
    }

    @Test
    void postPet_negativeWeight_returns400() throws Exception {
        mockMvc.perform(post("/profile/customer/me/pets")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad","species":"Cat","weightKg":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.weightKg").exists());
    }

    @Test
    void getById_withStaffToken_returns200() throws Exception {
        String customerToken = customerToken();
        String createResponse = mockMvc.perform(put("/profile/customer/me")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"0909000000"}
                                """))
                .andReturn().getResponse().getContentAsString();
        String profileId = createResponse.split("\"id\":\"")[1].split("\"")[0];

        String staffToken = TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("STAFF"));

        mockMvc.perform(get("/profile/customer/by-id/" + profileId).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0909000000"));
    }

    @Test
    void getById_withCustomerToken_returns403() throws Exception {
        mockMvc.perform(get("/profile/customer/by-id/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/profile/customer/by-id/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_unknownId_withAdminToken_returns404() throws Exception {
        String adminToken = TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("ADMIN"));

        mockMvc.perform(get("/profile/customer/by-id/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
