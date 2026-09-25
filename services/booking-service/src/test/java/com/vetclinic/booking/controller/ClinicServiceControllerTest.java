package com.vetclinic.booking.controller;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.support.TestJwtSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VD-21: danh mục dịch vụ. Ai cũng đọc được danh sách (trang chủ cần), nhưng chỉ ADMIN sửa, và
 * dịch vụ đã ngừng thì khách không thấy.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@AutoConfigureMockMvc
@Transactional
class ClinicServiceControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProfileServiceClient profileServiceClient;
    @MockBean private PetServiceClient petServiceClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String bearer(String role) {
        return "Bearer " + TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of(role));
    }

    private String body(String slug, String name) {
        return """
                {"slug":"%s","name":"%s","description":"Mo ta","durationMinutes":45}
                """.formatted(slug, name);
    }

    @Test
    void anyoneCanReadTheServiceList() throws Exception {
        // Trang chủ đọc khi khách chưa đăng nhập — bốn dịch vụ đã seed sẵn trong migration.
        mockMvc.perform(get("/booking/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].slug").value("kham-tong-quat"));
    }

    @Test
    void onlyAdminChangesTheCatalogue() throws Exception {
        for (String role : List.of("CUSTOMER", "DOCTOR", "STAFF")) {
            mockMvc.perform(post("/booking/services").header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body("sieu-am", "Sieu am")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminAddsAServiceThenStopsOfferingIt() throws Exception {
        MvcResult created = mockMvc.perform(post("/booking/services").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("sieu-am", "Sieu am")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // Trùng mã dịch vụ thì báo rõ, không tạo bản thứ hai.
        mockMvc.perform(post("/booking/services").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("sieu-am", "Sieu am 2")))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/booking/services/" + id + "/hide").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Ngừng cung cấp: khách không thấy nữa, nhưng phòng khám vẫn quản lý được.
        mockMvc.perform(get("/booking/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/booking/services").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(put("/booking/services/" + id + "/unhide").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void slugMustLookLikeASlug() throws Exception {
        mockMvc.perform(post("/booking/services").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"Sieu Am!\",\"name\":\"Sieu am\"}"))
                .andExpect(status().isBadRequest());
    }
}
