package com.vetclinic.pet.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Danh mục thuốc và đơn mẫu nằm ở product-service (cùng chỗ với kho). pet-service chỉ còn hỏi một
 * việc: giá niêm yết của các thuốc vừa kê, để ghi vào phiếu thu.
 */
@FeignClient(name = "product-service")
public interface ProductServiceClient {

    /** Khoá kết quả là đúng chuỗi tên đã gửi; thuốc ngoài danh mục hoặc chưa có giá thì vắng mặt. */
    @PostMapping("/medications/prices")
    Map<String, BigDecimal> pricesByName(@RequestBody List<String> names,
                                         @RequestHeader("Authorization") String bearerToken);
}
