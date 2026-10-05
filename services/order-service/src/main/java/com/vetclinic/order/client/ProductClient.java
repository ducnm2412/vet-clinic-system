package com.vetclinic.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Gọi product-service để lấy giá và kiểm tồn kho lúc checkout.
 *
 * Tên "product-service" tra qua Eureka nên không hardcode host/port. Endpoint
 * GET /products/{id} là công khai nên không cần đính token.
 *
 * Giá KHÔNG bao giờ lấy từ client gửi lên — nếu tin client thì khách sửa payload là mua
 * được giá tuỳ ý.
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/products/{id}")
    ProductView getProduct(@PathVariable("id") UUID id);

    /**
     * VD-14: trừ kho cho cả đơn, tất-cả-hoặc-không. Gọi lúc nhân viên xác nhận đơn và đợi kết
     * quả — thiếu hàng thì product-service trả 409 và không trừ dòng nào.
     *
     * Đính token của chính nhân viên đang bấm xác nhận: endpoint bên kia chỉ mở cho STAFF/ADMIN,
     * nên không cần danh tính riêng giữa hai service (VD-24 vẫn còn cho GET /products/{id}).
     */
    @PostMapping("/products/stock/deduct")
    StockDeductionResponse deductStock(@RequestBody StockDeductionRequest request,
                                       @RequestHeader("Authorization") String bearerToken);

    record StockDeductionRequest(UUID orderId, List<Line> lines) {
        public record Line(UUID productId, int quantity) {
        }
    }

    record StockDeductionResponse(UUID orderId, int applied) {
    }

    /** Chỉ khai những trường order-service thực sự dùng, không cần trùng khớp toàn bộ DTO bên kia. */
    record ProductView(
            UUID id,
            String sku,
            String name,
            BigDecimal price,
            String unit,
            Integer stockQuantity,
            Boolean active
    ) {
    }
}
