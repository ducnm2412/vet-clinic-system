package com.vetclinic.order.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vetclinic.order.dto.PaymentResultResponse;
import com.vetclinic.order.payment.GatewayUnavailableException;
import com.vetclinic.order.payment.InvalidGatewaySignatureException;
import com.vetclinic.order.payment.VnpayGateway;
import com.vetclinic.order.service.OnlinePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Hai đường VNPAY gọi về, cả hai không cần đăng nhập và dựa hoàn toàn vào chữ ký (kiểm trong
 * {@link VnpayGateway#verifyCallback}) rồi đi qua cùng một hàm ghi nhận không trùng lặp.
 */
@Slf4j
@RestController
@RequestMapping("/orders/pay/vnpay")
@RequiredArgsConstructor
public class VnpayController {

    private final OnlinePaymentService onlinePaymentService;
    private final ObjectProvider<VnpayGateway> vnpay;

    /** Phản hồi VNPAY yêu cầu cho IPN: JSON với đúng hai khoá RspCode và Message. */
    public record IpnResponse(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {
    }

    /**
     * IPN: VNPAY gọi từ máy chủ của họ, nguồn chính thức báo kết quả giao dịch. Mã trả về quyết định VNPAY
     * có gọi lại hay không: 00 và 02 là dừng, 01/04/97/99 là thử lại (tối đa 10 lần, cách 5 phút).
     * Trả 00 cả khi khách trả KHÔNG thành công — ta đã ghi nhận kết quả đó, gọi lại cũng chỉ ra như cũ.
     */
    @GetMapping("/ipn")
    public IpnResponse ipn(@RequestParam Map<String, String> params) {
        if (vnpay.getIfAvailable() == null) {
            return new IpnResponse("99", "VNPAY is not enabled");
        }
        try {
            return switch (onlinePaymentService.handleCallback(params).outcome()) {
                case PAID, FAILED -> new IpnResponse("00", "Confirm Success");
                case ALREADY_PAID -> new IpnResponse("02", "Order already confirmed");
                case UNKNOWN_TRANSACTION -> new IpnResponse("01", "Order not found");
                case AMOUNT_MISMATCH -> new IpnResponse("04", "Invalid amount");
            };
        } catch (InvalidGatewaySignatureException e) {
            return new IpnResponse("97", "Invalid Checksum");
        } catch (RuntimeException e) {
            // Giao dịch đã bị rollback; trả 99 để VNPAY gọi lại sau, không nuốt mất một khoản đã trả.
            log.error("Lỗi khi xử lý IPN của VNPAY", e);
            return new IpnResponse("99", "Unknown error");
        }
    }

    /**
     * Trang web gửi nguyên các tham số VNPAY gắn vào địa chỉ trả về của khách. Cũng ghi nhận kết quả như IPN
     * (an toàn vì có chữ ký, và không trùng lặp nếu IPN đã tới trước) để khách thấy đơn đã trả ngay cả khi
     * IPN chưa tới được máy chủ, ví dụ lúc chạy trên máy cá nhân không có địa chỉ công khai.
     */
    @GetMapping("/return")
    public PaymentResultResponse returned(@RequestParam Map<String, String> params) {
        if (vnpay.getIfAvailable() == null) {
            throw new GatewayUnavailableException();
        }
        return onlinePaymentService.handleCallback(params);
    }
}
