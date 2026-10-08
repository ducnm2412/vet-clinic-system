package com.vetclinic.order.controller;

import com.vetclinic.order.dto.MockPaymentSubmitRequest;
import com.vetclinic.order.dto.PaymentInitResponse;
import com.vetclinic.order.dto.PaymentResultResponse;
import com.vetclinic.order.security.jwt.AuthenticatedUser;
import com.vetclinic.order.service.OnlinePaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Thanh toán online cho đơn đặt qua giỏ hàng. Phân quyền khai trong SecurityConfig. */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OnlinePaymentController {

    private final OnlinePaymentService onlinePaymentService;

    /** Khách lấy link sang cổng trả tiền cho đơn của mình. */
    @PostMapping("/{id}/pay")
    public PaymentInitResponse startPayment(@PathVariable UUID id, Authentication authentication,
                                            HttpServletRequest request) {
        return onlinePaymentService.startPayment(userId(authentication), id, clientIp(request));
    }

    /** Cổng gọi về báo kết quả. Không cần đăng nhập, độ tin cậy dựa vào chữ ký. */
    @PostMapping("/pay/callback")
    public PaymentResultResponse callback(@RequestBody Map<String, String> params) {
        return onlinePaymentService.handleCallback(params);
    }

    /** Trang cổng giả lập gửi lựa chọn của khách (thành công / thất bại / huỷ). */
    @PostMapping("/pay/mock/submit")
    public PaymentResultResponse submitMock(@Valid @RequestBody MockPaymentSubmitRequest request,
                                            Authentication authentication) {
        return onlinePaymentService.submitMockPayment(userId(authentication), request);
    }

    /** Địa chỉ khách: ưu tiên X-Forwarded-For do gateway thêm, rơi về địa chỉ kết nối. Chỉ để cổng ghi nhận. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].strip()
                : request.getRemoteAddr();
        // Giá trị này đi vào link thanh toán nên chỉ nhận dạng địa chỉ IP, không nhận chuỗi tuỳ ý.
        return ip != null && ip.length() <= 45 && ip.matches("[0-9a-fA-F:.]+") ? ip : "127.0.0.1";
    }

    private UUID userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
