package com.vetclinic.order.controller;

import com.vetclinic.order.dto.AddToCartRequest;
import com.vetclinic.order.dto.CartResponse;
import com.vetclinic.order.dto.UpdateCartItemRequest;
import com.vetclinic.order.security.jwt.AuthenticatedUser;
import com.vetclinic.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** CN-32: giỏ hàng. Toàn bộ endpoint đều thao tác trên giỏ của chính người đang đăng nhập. */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse getMyCart(Authentication authentication) {
        return cartService.getMyCart(userId(authentication));
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddToCartRequest request,
                                Authentication authentication) {
        return cartService.addItem(userId(authentication), request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(@PathVariable UUID productId,
                                   @Valid @RequestBody UpdateCartItemRequest request,
                                   Authentication authentication) {
        return cartService.updateItemQuantity(userId(authentication), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable UUID productId, Authentication authentication) {
        return cartService.removeItem(userId(authentication), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(Authentication authentication) {
        cartService.clearCart(userId(authentication));
    }

    private UUID userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }
}
