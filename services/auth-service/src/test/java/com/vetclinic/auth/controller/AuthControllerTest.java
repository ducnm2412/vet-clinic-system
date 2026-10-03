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

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void register_thenVerify_thenLogin_thenMe_fullFlow() throws Exception {
        String registerBody = """
                {"firstName":"IT","lastName":"User","email":"it-user@example.com",
                 "password":"password123","confirmPassword":"password123"}
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message")
                        .value("Registration successful. Please check your email to verify your account."));

        String loginBody = """
                {"email":"it-user@example.com","password":"password123"}
                """;

        // Chưa verify email -> login phải bị chặn.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());

        User user = userRepository.findByEmail("it-user@example.com").orElseThrow();

        mockMvc.perform(get("/auth/verify-email").param("token", user.getVerificationToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));

        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("it-user@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String body = """
                {"firstName":"Dup","lastName":"User","email":"it-dup@example.com",
                 "password":"password123","confirmPassword":"password123"}
                """;

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered: it-dup@example.com"));
    }

    @Test
    void register_mismatchedPasswords_returns400WithFieldError() throws Exception {
        String body = """
                {"firstName":"Bad","lastName":"User","email":"it-badpw@example.com",
                 "password":"password123","confirmPassword":"different123"}
                """;

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.confirmPasswordMatching")
                        .value("password and confirmPassword must match"));
    }

    @Test
    void verifyEmail_unknownToken_returns400() throws Exception {
        mockMvc.perform(get("/auth/verify-email").param("token", "does-not-exist"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- VD-05: làm mới phiên ----------

    /**
     * Tạo tài khoản đã kích hoạt rồi đăng nhập, trả về nguyên response — access token nằm trong
     * thân JSON, refresh token nằm trong Set-Cookie httpOnly (không còn trong thân JSON nữa).
     */
    private MockHttpServletResponse loginFresh(String email) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"RT","lastName":"User","email":"%s","password":"password123","confirmPassword":"password123"}
                """.formatted(email))).andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        mockMvc.perform(get("/auth/verify-email").param("token", user.getVerificationToken()))
                .andExpect(status().isOk());

        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private static String refreshTokenCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie("refreshToken");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    @Test
    void refresh_withoutAccessToken_issuesPairThatWorksOnMe() throws Exception {
        // Toàn bộ lý do tồn tại của endpoint này: access token đã chết thì vẫn làm mới được,
        // nên nó KHÔNG được đòi Authorization header.
        String refreshToken = refreshTokenCookie(loginFresh("it-refresh@example.com"));

        MockHttpServletResponse response = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        // Xoay vòng: refresh token mới phải khác token cũ, và được cấp lại qua cookie.
        assertThat(refreshTokenCookie(response)).isNotEqualTo(refreshToken);

        String newAccess = objectMapper.readTree(response.getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("it-refresh@example.com"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        // 401 chứ không phải 400: frontend nhìn mã này để biết phải mời người dùng đăng nhập lại.
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", "khong-ton-tai")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_thenRefresh_returns401() throws Exception {
        String refreshToken = refreshTokenCookie(loginFresh("it-logout@example.com"));

        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Quên mật khẩu ----------

    @Test
    void forgotPassword_unknownEmail_returns200WithSameMessageAsKnownEmail() throws Exception {
        loginFresh("it-forgot@example.com");

        String known = mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"it-forgot@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-forgot@example.com\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(known);
    }

    @Test
    void forgotPassword_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_unknownToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"does-not-exist\",\"newPassword\":\"newpassword1\","
                                + "\"confirmPassword\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_mismatchedPasswords_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\",\"newPassword\":\"newpassword1\","
                                + "\"confirmPassword\":\"different1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.confirmPasswordMatching").exists());
    }
}
