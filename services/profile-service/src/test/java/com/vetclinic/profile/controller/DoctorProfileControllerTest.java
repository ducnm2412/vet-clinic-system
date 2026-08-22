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
class DoctorProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String doctorToken() {
        return TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("DOCTOR"));
    }

    @Test
    void listPublicDoctors_withoutToken_returns200() throws Exception {
        mockMvc.perform(get("/profile/doctors"))
                .andExpect(status().isOk());
    }

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/profile/doctor/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withCustomerToken_returns403() throws Exception {
        String customerToken = TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of("CUSTOMER"));

        mockMvc.perform(get("/profile/doctor/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void putMe_thenAppearsInPublicListing() throws Exception {
        String token = doctorToken();

        mockMvc.perform(put("/profile/doctor/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"specialty":"Ngoai khoa","phone":"0911111111","bio":"bio","yearsOfExperience":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialty").value("Ngoai khoa"));

        mockMvc.perform(get("/profile/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.specialty == 'Ngoai khoa')]").exists());
    }

    @Test
    void licenseFlow_createUpdateDelete() throws Exception {
        String token = doctorToken();

        String createResponse = mockMvc.perform(post("/profile/doctor/me/licenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licenseNumber":"VN-12345","issuedBy":"Bo Y Te","issuedDate":"2015-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String licenseId = createResponse.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(put("/profile/doctor/me/licenses/" + licenseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licenseNumber":"VN-99999","issuedBy":"Bo Y Te","issuedDate":"2015-01-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber").value("VN-99999"));
    }

    @Test
    void postLicense_missingIssuedDate_returns400() throws Exception {
        mockMvc.perform(post("/profile/doctor/me/licenses")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"licenseNumber":"X","issuedBy":"Y"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.issuedDate").exists());
    }
}
