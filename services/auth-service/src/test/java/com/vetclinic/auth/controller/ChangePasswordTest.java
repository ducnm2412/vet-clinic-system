package com.vetclinic.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /auth/change-password — đổi mật khẩu khi đang đăng nhập. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ChangePasswordTest {

    private static final String OLD = "oldpassword1";
    private static final String NEW = "newpassword2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private void activeAccount(String email) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Doi","lastName":"Mk","email":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(email, OLD, OLD))).andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(get("/auth/verify-email").param("token", user.getVerificationToken()))
                .andExpect(status().isOk());
    }

    /** Một lần đăng nhập = một thiết bị. */
    private MockHttpServletResponse login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn().getResponse();
    }

    private String accessOf(MockHttpServletResponse login) throws Exception {
        return objectMapper.readTree(login.getContentAsString()).get("accessToken").asText();
    }

    private Cookie refreshOf(MockHttpServletResponse login) {
        return login.getCookie("refreshToken");
    }

    private MockHttpServletResponse change(MockHttpServletResponse device, String current, String next, String confirm)
            throws Exception {
        var request = post("/auth/change-password")
                .header("Authorization", "Bearer " + accessOf(device))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                        .formatted(current, next, confirm));
        if (refreshOf(device) != null) request.cookie(refreshOf(device));
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void change_success_newPasswordWorks_oldOneDoesNot() throws Exception {
        activeAccount("cp-ok@example.com");
        MockHttpServletResponse device = login("cp-ok@example.com", OLD);

        assertThat(change(device, OLD, NEW, NEW).getStatus()).isEqualTo(200);

        assertThat(login("cp-ok@example.com", NEW).getStatus()).isEqualTo(200);
        assertThat(login("cp-ok@example.com", OLD).getStatus()).isEqualTo(401);
    }

    @Test
    void change_wrongCurrentPassword_is400WithFieldError_notAnExpiredSession() throws Exception {
        activeAccount("cp-wrong@example.com");
        MockHttpServletResponse device = login("cp-wrong@example.com", OLD);

        // 400 chứ không phải 401, nếu không frontend tưởng hết phiên và đăng xuất người dùng.
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + accessOf(device))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"saimatkhau\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                                .formatted(NEW, NEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").value("Mật khẩu hiện tại không đúng"));

        // Mật khẩu không bị đổi.
        assertThat(login("cp-wrong@example.com", OLD).getStatus()).isEqualTo(200);
    }

    @Test
    void change_sameAsCurrent_isRefused() throws Exception {
        activeAccount("cp-same@example.com");
        MockHttpServletResponse device = login("cp-same@example.com", OLD);

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + accessOf(device))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                                .formatted(OLD, OLD, OLD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").isNotEmpty());
    }

    @Test
    void change_invalidInput_returns400() throws Exception {
        activeAccount("cp-invalid@example.com");
        MockHttpServletResponse device = login("cp-invalid@example.com", OLD);

        assertThat(change(device, OLD, "short", "short").getStatus()).isEqualTo(400);        // < 8 ký tự
        assertThat(change(device, OLD, NEW, "khac-hoan-toan").getStatus()).isEqualTo(400);   // xác nhận không khớp
        assertThat(change(device, "", NEW, NEW).getStatus()).isEqualTo(400);                 // thiếu mật khẩu hiện tại
    }

    @Test
    void change_withoutLogin_returns401() throws Exception {
        mockMvc.perform(post("/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                                .formatted(NEW, NEW)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void change_keepsThisDeviceLoggedIn_butSignsOutTheOthers() throws Exception {
        activeAccount("cp-devices@example.com");
        MockHttpServletResponse thisDevice = login("cp-devices@example.com", OLD);
        MockHttpServletResponse otherDevice = login("cp-devices@example.com", OLD);

        assertThat(change(thisDevice, OLD, NEW, NEW).getStatus()).isEqualTo(200);

        // Thiết bị vừa đổi vẫn làm mới được phiên; thiết bị kia thì không (mật khẩu có thể đã bị lộ).
        mockMvc.perform(post("/auth/refresh").cookie(refreshOf(thisDevice))).andExpect(status().isOk());
        mockMvc.perform(post("/auth/refresh").cookie(refreshOf(otherDevice))).andExpect(status().isUnauthorized());
    }

    @Test
    void change_accountWithoutPassword_pointsToForgotPassword() throws Exception {
        activeAccount("cp-social@example.com");
        MockHttpServletResponse device = login("cp-social@example.com", OLD);
        User user = userRepository.findByEmail("cp-social@example.com").orElseThrow();
        user.setPasswordHash(null); // như tài khoản tạo qua Google/Facebook
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + accessOf(device))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}"
                                .formatted(OLD, NEW, NEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Quên mật khẩu")));
    }
}
