package com.vetclinic.order.payment;

import com.vetclinic.order.domain.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Cổng "chuyển khoản qua mã QR": khách được đưa tới trang hiện mã QR (số tiền, nội dung đã điền sẵn),
 * quét bằng app ngân hàng/ví rồi chuyển. Không có cổng bên thứ ba nào báo kết quả về, nên phòng khám
 * đối chiếu tiền vào tài khoản rồi nhân viên xác nhận (xem OnlinePaymentService#confirmTransferReceived).
 *
 * Vì không có bên nào ký kết quả, cổng này KHÔNG nhận kết quả qua đường callback công khai: nếu nhận,
 * ai cũng gọi được để "báo đã trả". Chỉ bật khi order.qr-gateway.enabled=true, không bật cùng cổng khác.
 */
@Component
@ConditionalOnProperty(name = "order.qr-gateway.enabled", havingValue = "true")
public class QrTransferGateway implements OnlinePaymentGateway {

    private final String pageUrl;

    public QrTransferGateway(@Value("${order.qr-gateway.page-url:/pay/qr}") String pageUrl) {
        this.pageUrl = pageUrl;
    }

    @Override
    public String buildPaymentUrl(Order order, String txnRef, String clientIp) {
        char separator = pageUrl.contains("?") ? '&' : '?';
        return pageUrl + separator + "orderId="
                + URLEncoder.encode(order.getId().toString(), StandardCharsets.UTF_8);
    }

    @Override
    public GatewayResult verifyCallback(Map<String, String> params) {
        throw new InvalidGatewaySignatureException(
                "Cổng chuyển khoản QR không nhận kết quả từ bên ngoài, nhân viên xác nhận trên trang quản lý");
    }
}
