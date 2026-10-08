package com.vetclinic.order.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.order.domain.Order;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cổng VNPAY (API thanh toán phiên bản 2.1.0). Chỉ bật khi order.vnpay.enabled=true; không được bật
 * cùng cổng giả lập.
 *
 * Chữ ký: HMAC-SHA512 trên chuỗi "tên=giá trị" của các tham số vnp_* đã sắp theo tên, giá trị được
 * URL-encode, bỏ tham số rỗng, không tính chính vnp_SecureHash. Cùng thuật toán cho cả link gửi đi lẫn
 * kết quả (IPN và trang trả về) nhận về.
 */
@Component
@ConditionalOnProperty(name = "order.vnpay.enabled", havingValue = "true")
public class VnpayGateway implements OnlinePaymentGateway {

    static final String HASH = "vnp_SecureHash";
    static final String HASH_TYPE = "vnp_SecureHashType";

    /** VNPAY ghi ngày giờ theo giờ Việt Nam (GMT+7), không theo UTC. */
    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VN);

    /** Nếu đơn không có hạn riêng thì cho khách chừng này để trả. */
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

    private final String tmnCode;
    private final byte[] hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final String queryUrl;
    private final String serverIp;
    private final ObjectMapper objectMapper;
    private final VnpayHttp http;

    /** Bản dùng cho test chữ ký và dựng link: không gọi mạng nên http luôn báo lỗi. */
    public VnpayGateway(String tmnCode, String hashSecret, String payUrl, String returnUrl) {
        this(tmnCode, hashSecret, payUrl, returnUrl, "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                "127.0.0.1", new ObjectMapper(), (url, json) -> {
                    throw new IOException("Chưa cấu hình kết nối tới VNPAY");
                });
    }

    @Autowired
    public VnpayGateway(@Value("${order.vnpay.tmn-code:}") String tmnCode,
                        @Value("${order.vnpay.hash-secret:}") String hashSecret,
                        @Value("${order.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
                        @Value("${order.vnpay.return-url:}") String returnUrl,
                        @Value("${order.vnpay.query-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String queryUrl,
                        @Value("${order.vnpay.server-ip:127.0.0.1}") String serverIp,
                        ObjectMapper objectMapper,
                        VnpayHttp http) {
        requireText(tmnCode, "order.vnpay.tmn-code");
        requireText(hashSecret, "order.vnpay.hash-secret");
        requireText(payUrl, "order.vnpay.pay-url");
        requireText(returnUrl, "order.vnpay.return-url");
        this.tmnCode = tmnCode.strip();
        this.hashSecret = hashSecret.strip().getBytes(StandardCharsets.UTF_8);
        this.payUrl = payUrl.strip();
        this.returnUrl = returnUrl.strip();
        this.queryUrl = queryUrl.strip();
        this.serverIp = serverIp.strip();
        this.objectMapper = objectMapper;
        this.http = http;
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " phải được đặt khi bật cổng thanh toán VNPAY");
        }
    }

    @Override
    public String buildPaymentUrl(Order order, String txnRef, String clientIp) {
        return buildPaymentUrl(order, txnRef, clientIp, Instant.now());
    }

    String buildPaymentUrl(Order order, String txnRef, String clientIp, Instant now) {
        Instant expires = order.getPaymentExpiresAt() != null && order.getPaymentExpiresAt().isAfter(now)
                ? order.getPaymentExpiresAt()
                : now.plus(DEFAULT_WINDOW);

        // Mốc tạo link đã lưu trên đơn (để hỏi lại giao dịch sau này đúng mốc); chưa có thì lấy lúc này.
        Instant created = order.getGatewayTxnCreatedAt() != null ? order.getGatewayTxnCreatedAt() : now;

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        // VNPAY nhận số tiền nhân 100 để bỏ phần thập phân.
        params.put("vnp_Amount", order.getTotal().setScale(0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                .toPlainString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + asciiOnly(order.getOrderCode()));
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", STAMP.format(created));
        params.put("vnp_ExpireDate", STAMP.format(expires));

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return payUrl + "?" + query + "&" + HASH + "=" + sign(params);
    }

    @Override
    public GatewayResult verifyCallback(Map<String, String> params) {
        if (params == null || params.get(HASH) == null || params.get(HASH).isBlank()) {
            throw new InvalidGatewaySignatureException("Thiếu chữ ký VNPAY");
        }
        Map<String, String> signed = new TreeMap<>(params);
        signed.remove(HASH);
        signed.remove(HASH_TYPE);

        byte[] expected = sign(signed).getBytes(StandardCharsets.UTF_8);
        byte[] actual = params.get(HASH).strip().toLowerCase().getBytes(StandardCharsets.UTF_8);
        // So sánh thời gian không đổi để không lộ chữ ký đúng qua độ trễ phản hồi.
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidGatewaySignatureException("Chữ ký VNPAY không hợp lệ");
        }
        // Chữ ký đúng nhưng của một terminal khác (cùng secret dùng lại) cũng không phải giao dịch của ta.
        if (!tmnCode.equals(params.get("vnp_TmnCode"))) {
            throw new InvalidGatewaySignatureException("Kết quả VNPAY không thuộc terminal này");
        }

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            throw new InvalidGatewaySignatureException("Kết quả VNPAY thiếu mã giao dịch");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(params.get("vnp_Amount")).movePointLeft(2);
        } catch (RuntimeException e) {
            throw new InvalidGatewaySignatureException("Số tiền từ VNPAY không hợp lệ");
        }
        // Thành công khi cả mã phản hồi lẫn trạng thái giao dịch đều là 00.
        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        return new GatewayResult(txnRef, amount, success, params.get("vnp_TransactionNo"));
    }

    /**
     * Hỏi VNPAY trạng thái thật của giao dịch hiện tại của đơn (API querydr). Cả yêu cầu lẫn phản hồi đều có
     * chữ ký HMAC-SHA512 trên các trường nối bằng dấu "|" theo thứ tự cố định trong tài liệu.
     */
    @Override
    public Optional<GatewayResult> queryTransaction(Order order) {
        if (order.getGatewayTxnRef() == null || order.getGatewayTxnCreatedAt() == null) {
            // Chưa từng cấp link (hoặc link cấp trước khi lưu mốc tạo) thì không có gì để hỏi.
            return Optional.empty();
        }
        String txnRef = order.getGatewayTxnRef();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String transactionDate = STAMP.format(order.getGatewayTxnCreatedAt());
        String createDate = STAMP.format(Instant.now());
        String orderInfo = "Truy van giao dich " + asciiOnly(order.getOrderCode());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", tmnCode);
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", serverIp);
        body.put(HASH, hmac(String.join("|", requestId, "2.1.0", "querydr", tmnCode, txnRef,
                transactionDate, createDate, serverIp, orderInfo)));

        JsonNode response;
        try {
            response = objectMapper.readTree(http.postJson(queryUrl, objectMapper.writeValueAsString(body)));
        } catch (IOException e) {
            throw new GatewayQueryException("Không hỏi được VNPAY về giao dịch " + txnRef, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayQueryException("Bị ngắt khi hỏi VNPAY về giao dịch " + txnRef, e);
        }

        // Phản hồi phải đúng chữ ký thì mới tin; thiếu trường thì coi như rỗng khi nối chuỗi.
        String signedData = String.join("|", text(response, "vnp_ResponseId"), text(response, "vnp_Command"),
                text(response, "vnp_ResponseCode"), text(response, "vnp_Message"), text(response, "vnp_TmnCode"),
                text(response, "vnp_TxnRef"), text(response, "vnp_Amount"), text(response, "vnp_BankCode"),
                text(response, "vnp_PayDate"), text(response, "vnp_TransactionNo"),
                text(response, "vnp_TransactionType"), text(response, "vnp_TransactionStatus"),
                text(response, "vnp_OrderInfo"), text(response, "vnp_PromotionCode"),
                text(response, "vnp_PromotionAmount"));
        if (!MessageDigest.isEqual(hmac(signedData).getBytes(StandardCharsets.UTF_8),
                text(response, HASH).strip().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new GatewayQueryException("Phản hồi của VNPAY về giao dịch " + txnRef + " sai chữ ký");
        }

        String responseCode = text(response, "vnp_ResponseCode");
        if ("91".equals(responseCode)) {
            return Optional.empty();
        }
        if (!"00".equals(responseCode)) {
            throw new GatewayQueryException("VNPAY trả mã " + responseCode + " khi hỏi giao dịch " + txnRef);
        }
        if (!tmnCode.equals(text(response, "vnp_TmnCode")) || !txnRef.equals(text(response, "vnp_TxnRef"))) {
            throw new GatewayQueryException("Phản hồi của VNPAY không phải của giao dịch " + txnRef);
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(text(response, "vnp_Amount")).movePointLeft(2);
        } catch (RuntimeException e) {
            throw new GatewayQueryException("Số tiền trong phản hồi của VNPAY không hợp lệ");
        }
        // Chỉ tính là đã trả khi đây là giao dịch thanh toán (loại 01) và trạng thái 00; bản ghi hoàn tiền thì không.
        String type = text(response, "vnp_TransactionType");
        boolean paid = "00".equals(text(response, "vnp_TransactionStatus")) && (type.isEmpty() || "01".equals(type));
        return Optional.of(new GatewayResult(txnRef, amount, paid, text(response, "vnp_TransactionNo")));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    /** Chữ ký của một tập tham số; bỏ tham số rỗng như tài liệu VNPAY yêu cầu. */
    String sign(Map<String, String> params) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            String value = e.getValue();
            if (value == null || value.isEmpty() || HASH.equals(e.getKey()) || HASH_TYPE.equals(e.getKey())) {
                continue;
            }
            if (!data.isEmpty()) {
                data.append('&');
            }
            data.append(e.getKey()).append('=').append(encode(value));
        }
        return hmac(data.toString());
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret, "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không ký được dữ liệu VNPAY", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    /** Nội dung đơn gửi VNPAY phải không dấu và không ký tự đặc biệt. */
    static String asciiOnly(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return plain.replaceAll("[^A-Za-z0-9 ]", "");
    }
}
