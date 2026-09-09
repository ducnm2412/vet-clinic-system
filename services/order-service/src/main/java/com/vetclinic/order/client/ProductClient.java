package com.vetclinic.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
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
