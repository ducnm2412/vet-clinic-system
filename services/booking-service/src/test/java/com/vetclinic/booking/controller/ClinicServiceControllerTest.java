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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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

    // Mã chưa có trong danh mục seed.
    private static final String NEW_SLUG = "dich-vu-moi-cua-test";

    private String bearer(String role) {
        return "Bearer " + TestJwtSupport.token(jwtSecret, UUID.randomUUID(), List.of(role));
    }

    private String body(String slug, String name) {
        return """
                {"slug":"%s","name":"%s","description":"Mo ta","durationMinutes":45}
                """.formatted(slug, name);
    }

    // Danh mục dịch vụ được seed sẵn bằng migration và sẽ còn đổi theo phòng khám, nên test không
    // cố định số lượng: chỉ kiểm tra những dịch vụ chắc chắn có và dịch vụ chắc chắn đã ẩn.
    @Test
    void anyoneCanReadTheServiceList() throws Exception {
        // Trang chủ đọc khi khách chưa đăng nhập.
        mockMvc.perform(get("/booking/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItems("kham-tong-quat", "tiem-phong", "phau-thuat")))
                // Dịch vụ đã ngừng cung cấp thì khách không thấy.
                .andExpect(jsonPath("$[*].slug", not(hasItem("cham-soc-lam-dep"))));
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
        // Đếm trước khi thêm: số dịch vụ có sẵn do migration seed quyết định, không phải test này.
        int visibleBefore = count(get("/booking/services"));
        int allBefore = count(get("/booking/services").header("Authorization", bearer("ADMIN")));

        MvcResult created = mockMvc.perform(post("/booking/services").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(NEW_SLUG, "Dich vu moi")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // Trùng mã dịch vụ thì báo rõ, không tạo bản thứ hai.
        mockMvc.perform(post("/booking/services").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(NEW_SLUG, "Dich vu moi 2")))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/booking/services/" + id + "/hide").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Ngừng cung cấp: khách không thấy nữa, nhưng phòng khám vẫn quản lý được.
        org.assertj.core.api.Assertions.assertThat(count(get("/booking/services"))).isEqualTo(visibleBefore);
        org.assertj.core.api.Assertions
                .assertThat(count(get("/booking/services").header("Authorization", bearer("ADMIN"))))
                .isEqualTo(allBefore + 1);

        mockMvc.perform(put("/booking/services/" + id + "/unhide").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    private int count(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String json = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<java.util.List<?>>read(json, "$").size();
    }

    @Test
    void slugMustLookLikeASlug() throws Exception {
        mockMvc.perform(post("/booking/services").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"Sieu Am!\",\"name\":\"Sieu am\"}"))
                .andExpect(status().isBadRequest());
    }
}
