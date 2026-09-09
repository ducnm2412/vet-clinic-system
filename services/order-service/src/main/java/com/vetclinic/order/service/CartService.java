package com.vetclinic.order.service;

import com.vetclinic.order.client.ProductClient;
import com.vetclinic.order.domain.Cart;
import com.vetclinic.order.domain.CartItem;
import com.vetclinic.order.dto.CartItemResponse;
import com.vetclinic.order.dto.CartResponse;
import com.vetclinic.order.exception.ProductUnavailableException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.repository.CartRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** CN-32: giỏ hàng. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductClient productClient;

    @Transactional
    public CartResponse getMyCart(UUID userId) {
        return toResponse(getOrCreateCart(userId));
    }

    @Transactional
    public CartResponse addItem(UUID userId, UUID productId, int quantity) {
        // Kiểm tra sản phẩm có thật và còn bán ngay lúc thêm, để khách biết sớm
        // thay vì đến bước checkout mới báo lỗi.
        ProductClient.ProductView product = fetchProduct(productId);
        if (Boolean.FALSE.equals(product.active())) {
            throw new ProductUnavailableException("Sản phẩm đã ngừng bán: " + product.name());
        }

        Cart cart = getOrCreateCart(userId);

        CartItem existing = findItem(cart, productId);
        if (existing != null) {
            // Thêm lại sản phẩm đã có thì cộng dồn, không tạo dòng thứ hai.
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .productId(productId)
                    .quantity(quantity)
                    .build());
        }

        cartRepository.saveAndFlush(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(UUID userId, UUID productId, int quantity) {
        Cart cart = getOrCreateCart(userId);

        CartItem item = findItem(cart, productId);
        if (item == null) {
            throw new ResourceNotFoundException("Sản phẩm không có trong giỏ: " + productId);
        }

        item.setQuantity(quantity);
        cartRepository.saveAndFlush(cart);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID productId) {
        Cart cart = getOrCreateCart(userId);

        boolean removed = cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        if (!removed) {
            throw new ResourceNotFoundException("Sản phẩm không có trong giỏ: " + productId);
        }

        cartRepository.saveAndFlush(cart);
        return toResponse(cart);
    }

    @Transactional
    public void clearCart(UUID userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cartRepository.saveAndFlush(cart);
    }

    /** Dùng lại bởi OrderService sau khi đặt hàng xong. */
    @Transactional
    public Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));
    }

    /**
     * Lấy thông tin sản phẩm từ product-service. Sản phẩm bị xoá trả 404 — đổi thành lỗi
     * nghiệp vụ của mình thay vì để FeignException lọt lên tầng trên.
     */
    public ProductClient.ProductView fetchProduct(UUID productId) {
        try {
            return productClient.getProduct(productId);
        } catch (FeignException.NotFound e) {
            throw new ProductUnavailableException("Sản phẩm không còn tồn tại: " + productId);
        }
    }

    private CartItem findItem(Cart cart, UUID productId) {
        return cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Dựng response kèm giá và tình trạng còn hàng lấy trực tiếp từ product-service.
     * Giỏ chỉ lưu productId + quantity, nên giá luôn là giá mới nhất chứ không phải giá
     * lúc bỏ vào giỏ — đúng với cách các sàn thương mại điện tử hoạt động.
     */
    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean checkoutable = true;

        for (CartItem item : cart.getItems()) {
            ProductClient.ProductView p;
            try {
                p = productClient.getProduct(item.getProductId());
            } catch (FeignException.NotFound e) {
                items.add(new CartItemResponse(item.getProductId(), null, "(sản phẩm đã bị xoá)",
                        null, null, item.getQuantity(), null, false, "Sản phẩm không còn tồn tại"));
                checkoutable = false;
                continue;
            }

            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(item.getQuantity()));

            String reason = null;
            if (Boolean.FALSE.equals(p.active())) {
                reason = "Sản phẩm đã ngừng bán";
            } else if (p.stockQuantity() < item.getQuantity()) {
                reason = "Chỉ còn " + p.stockQuantity() + " " + p.unit();
            }

            if (reason != null) {
                checkoutable = false;
            } else {
                subtotal = subtotal.add(lineTotal);
            }

            items.add(new CartItemResponse(p.id(), p.sku(), p.name(), p.unit(), p.price(),
                    item.getQuantity(), lineTotal, reason == null, reason));
        }

        return new CartResponse(items, items.size(), subtotal, checkoutable && !items.isEmpty());
    }
}
