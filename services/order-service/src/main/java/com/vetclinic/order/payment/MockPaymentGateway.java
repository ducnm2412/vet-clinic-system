package com.vetclinic.order.payment;

import com.vetclinic.order.domain.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cổng giả lập dùng để chạy thử luồng thanh toán online khi chưa có tài khoản merchant.
 *
 * Mô phỏng đúng cách cổng thật làm việc: kết quả báo về luôn kèm chữ ký HMAC-SHA256 và phía đơn hàng
 * chỉ tin kết quả qua kiểm tra chữ ký. Chỉ bật khi order.mock-gateway.enabled=true — môi trường thật
 * không được bật, vì cổng này cho phép "trả tiền" mà không có tiền thật.
 */
@Component
@ConditionalOnProperty(name = "order.mock-gateway.enabled", havingValue = "true")
public class MockPaymentGateway implements OnlinePaymentGateway {

    static final String SIGNATURE = "signature";

    public enum Outcome { SUCCESS, FAILED, CANCELLED }

    private final byte[] secret;
    private final String pageUrl;

    public MockPaymentGateway(@Value("${order.mock-gateway.secret:}") String secret,
                              @Value("${order.mock-gateway.page-url:/pay/mock}") String pageUrl) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "order.mock-gateway.secret phải được đặt khi bật cổng thanh toán giả lập");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.pageUrl = pageUrl;
    }

    @Override
    public String buildPaymentUrl(Order order, String txnRef, String clientIp) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("txnRef", txnRef);
        params.put("orderCode", order.getOrderCode());
        params.put("amount", formatAmount(order.getTotal()));
        params.put(SIGNATURE, sign(params));

        StringBuilder url = new StringBuilder(pageUrl);
        char separator = pageUrl.contains("?") ? '&' : '?';
        for (Map.Entry<String, String> e : params.entrySet()) {
            url.append(separator).append(e.getKey()).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            separator = '&';
        }
        return url.toString();
    }

    /** Trang giả lập gửi lại đúng các tham số của link thanh toán; sai chữ ký nghĩa là link bị sửa. */
    public void verifyPaymentRequest(String txnRef, String orderCode, String amount, String signature) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("txnRef", txnRef);
        params.put("orderCode", orderCode);
        params.put("amount", amount);
        requireValid(params, signature);
    }

    /** Đóng vai cổng: dựng tham số kết quả đã ký, y hệt thứ cổng thật sẽ gọi về. */
    public Map<String, String> buildCallback(String txnRef, String amount, Outcome outcome) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("txnRef", txnRef);
        params.put("amount", amount);
        params.put("status", outcome.name());
        params.put("transactionNo", "MOCK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        params.put(SIGNATURE, sign(params));
        return params;
    }

    @Override
    public GatewayResult verifyCallback(Map<String, String> params) {
        String signature = params == null ? null : params.get(SIGNATURE);
        requireValid(params, signature);

        String txnRef = params.get("txnRef");
        String status = params.get("status");
        if (txnRef == null || txnRef.isBlank() || status == null) {
            throw new InvalidGatewaySignatureException("Kết quả từ cổng thiếu mã giao dịch hoặc trạng thái");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(params.get("amount"));
        } catch (RuntimeException e) {
            throw new InvalidGatewaySignatureException("Số tiền từ cổng không hợp lệ");
        }
        return new GatewayResult(txnRef, amount, Outcome.SUCCESS.name().equals(status),
                params.get("transactionNo"));
    }

    /** Số tiền gửi cổng là đồng nguyên, không phần thập phân. */
    public static String formatAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private void requireValid(Map<String, String> params, String signature) {
        if (params == null || signature == null) {
            throw new InvalidGatewaySignatureException("Thiếu chữ ký");
        }
        Map<String, String> unsigned = new LinkedHashMap<>(params);
        unsigned.remove(SIGNATURE);
        byte[] expected = sign(unsigned).getBytes(StandardCharsets.UTF_8);
        // So sánh thời gian không đổi để không lộ chữ ký đúng qua độ trễ phản hồi.
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidGatewaySignatureException("Chữ ký không hợp lệ");
        }
    }

    private String sign(Map<String, String> params) {
        // Sắp xếp theo tên tham số để hai phía luôn ra cùng một chuỗi, bất kể thứ tự gửi.
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            if (SIGNATURE.equals(e.getKey()) || e.getValue() == null) {
                continue;
            }
            if (!canonical.isEmpty()) {
                canonical.append('&');
            }
            canonical.append(e.getKey()).append('=').append(e.getValue());
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không ký được dữ liệu cổng thanh toán", e);
        }
    }
}
