package com.vetclinic.order.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phân quyền thật qua toàn bộ chuỗi filter: token đúng chữ ký của từng vai trò đi vào từng đường dẫn
 * và xem server trả 401/403 hay cho qua. Cho qua được kiểm bằng 400 (thân yêu cầu cố tình rỗng nên
 * bị validate chặn) — nghĩa là đã qua phân quyền mà chưa chạm tới nghiệp vụ hay kho.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "order.mock-gateway.enabled=false"
})
@AutoConfigureMockMvc
class ManageEndpointsSecurityTest {

    @Autowired private MockMvc mvc;
    @Value("${jwt.secret}") private String jwtSecret;

    private String token(String role) {
        return "Bearer " + Jwts.builder()
                .subject(role.toLowerCase() + "@test.local")
                .claim("userId", UUID.randomUUID().toString())
                .claim("roles", List.of(role))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private int send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getStatus();
    }

    @Test
    void counterInvoice_rejectsAnonymousAndCustomer() throws Exception {
        assertThat(send(post("/orders/manage/counter").content("{}"))).isEqualTo(401);
        assertThat(send(post("/orders/manage/counter").content("{}").header("Authorization", token("CUSTOMER"))))
                .isEqualTo(403);
    }

    @Test
    void counterInvoice_letsStaffAndAdminThroughToValidation() throws Exception {
        assertThat(send(post("/orders/manage/counter").content("{}").header("Authorization", token("STAFF"))))
                .isEqualTo(400);
        assertThat(send(post("/orders/manage/counter").content("{}").header("Authorization", token("ADMIN"))))
                .isEqualTo(400);
    }

    @Test
    void counterInvoice_rejectsATokenSignedWithAnotherKey() throws Exception {
        String forged = "Bearer " + Jwts.builder()
                .claim("userId", UUID.randomUUID().toString())
                .claim("roles", List.of("ADMIN"))
                .signWith(Keys.hmacShaKeyFor("mot-khoa-khac-hoan-toan-32-ky-tu-tro-len".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(send(post("/orders/manage/counter").content("{}").header("Authorization", forged))).isEqualTo(401);
    }

    @Test
    void orderProcessingSteps_areStaffOnly() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String step : List.of("confirm", "ship", "complete")) {
            assertThat(send(post("/orders/manage/" + id + "/" + step).header("Authorization", token("CUSTOMER"))))
                    .as(step).isEqualTo(403);
        }
        assertThat(send(post("/orders/manage/" + id + "/cancel").content("{}")
                .header("Authorization", token("CUSTOMER")))).isEqualTo(403);
    }

    @Test
    void startingAnOnlinePayment_needsLogin() throws Exception {
        assertThat(send(post("/orders/" + UUID.randomUUID() + "/pay"))).isEqualTo(401);
        assertThat(send(post("/orders/pay/mock/submit").content("{}"))).isEqualTo(401);
    }

    @Test
    void gatewayCallback_isReachableWithoutLogin() throws Exception {
        // Cổng gọi từ máy chủ của họ nên không có token. Ở đây chưa bật cổng nào nên trả 503,
        // quan trọng là KHÔNG bị chặn 401/403 trước khi tới bước kiểm tra chữ ký.
        int status = send(post("/orders/pay/callback").content("{\"txnRef\":\"x\"}"));

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void vnpayCallbacks_areReachableWithoutLoginButDoNothingWhenVnpayIsOff() throws Exception {
        // VNPAY gọi IPN từ máy chủ của họ và khách bị đưa về trang trả về: không có token. Khi chưa bật VNPAY
        // thì IPN báo lỗi 99 (HTTP 200 đúng định dạng VNPAY) và trang trả về báo dịch vụ chưa khả dụng.
        mvc.perform(get("/orders/pay/vnpay/ipn")).andExpect(status().isOk());
        assertThat(mvc.perform(get("/orders/pay/vnpay/ipn")).andReturn().getResponse().getContentAsString())
                .contains("\"RspCode\":\"99\"");
        assertThat(mvc.perform(get("/orders/pay/vnpay/return")).andReturn().getResponse().getStatus()).isEqualTo(503);
    }
}
