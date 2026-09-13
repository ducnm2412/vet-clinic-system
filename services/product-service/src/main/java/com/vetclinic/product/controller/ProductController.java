package com.vetclinic.product.controller;

import com.vetclinic.product.dto.PageResponse;
import com.vetclinic.product.dto.ProductRequest;
import com.vetclinic.product.dto.ProductResponse;
import com.vetclinic.product.dto.StockAdjustmentRequest;
import com.vetclinic.product.dto.StockMovementResponse;
import com.vetclinic.product.security.jwt.AuthenticatedUser;
import com.vetclinic.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CN-28, CN-29, CN-30. Phân quyền khai trong SecurityConfig. */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ---------- CN-29: công khai ----------

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            // Mặc định chỉ trả hàng đang bán; STAFF/ADMIN muốn xem cả hàng ẩn thì truyền activeOnly=false.
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        // VD-01: endpoint này công khai, nên `activeOnly` do client gửi không được tin. Trước đây
        // ai cũng thêm ?activeOnly=false là liệt kê được hàng đã ngừng bán. Giờ chỉ người quản
        // lý kho mới được tắt bộ lọc; khách vãng lai và khách hàng luôn bị ép về hàng đang bán.
        boolean effectiveActiveOnly = activeOnly || !canSeeInactive(authentication);
        return productService.search(categoryId, keyword, minPrice, maxPrice, effectiveActiveOnly, pageable);
    }

    /**
     * Chỉ STAFF/ADMIN mới thấy hàng đã ẩn. Route này permitAll nên `authentication` là null
     * với khách vãng lai — JwtAuthFilter chỉ dựng nó khi có token hợp lệ.
     */
    private static boolean canSeeInactive(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_STAFF") || a.equals("ROLE_ADMIN"));
    }

    // ---------- CN-30: tồn kho (STAFF/ADMIN) ----------
    // Khai TRƯỚC /{id} để "low-stock" không bị bắt nhầm thành một UUID.

    @GetMapping("/low-stock")
    public List<ProductResponse> lowStock() {
        return productService.listLowStock();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    @GetMapping("/{id}/stock-movements")
    public List<StockMovementResponse> stockMovements(@PathVariable UUID id) {
        return productService.listMovements(id);
    }

    @PostMapping("/{id}/stock")
    public ProductResponse adjustStock(@PathVariable UUID id,
                                       @Valid @RequestBody StockAdjustmentRequest request,
                                       Authentication authentication) {
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return productService.adjustStock(id, request, principal.userId());
    }

    // ---------- CN-28: quản lý sản phẩm (ADMIN) ----------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        productService.delete(id);
    }
}
