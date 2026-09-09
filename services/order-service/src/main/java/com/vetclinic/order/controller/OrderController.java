package com.vetclinic.order.controller;

import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.dto.CancelOrderRequest;
import com.vetclinic.order.dto.CheckoutRequest;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.dto.OrderStatusHistoryResponse;
import com.vetclinic.order.dto.OrderSummaryResponse;
import com.vetclinic.order.dto.PageResponse;
import com.vetclinic.order.dto.UpdateStatusRequest;
import com.vetclinic.order.security.jwt.AuthenticatedUser;
import com.vetclinic.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CN-33, CN-35, CN-36. Phân quyền khai trong SecurityConfig.
 *
 * Đường dẫn cho nhân viên đặt dưới /orders/manage/** thay vì /admin/** — gateway đã dành
 * /admin/** cho auth-service.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ---------- CN-33, CN-35: khách hàng ----------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest request,
                                  Authentication authentication) {
        return orderService.checkout(userId(authentication), request);
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> listMyOrders(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        return orderService.listMyOrders(userId(authentication), pageable);
    }

    // ---------- CN-36: nhân viên ----------
    // Khai TRƯỚC /{id} để "manage" không bị bắt nhầm thành một UUID.

    @GetMapping("/manage")
    public PageResponse<OrderSummaryResponse> listAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.listAllOrders(status, pageable);
    }

    @GetMapping("/manage/{id}")
    public OrderResponse getOrderAsStaff(@PathVariable UUID id) {
        return orderService.getOrderAsStaff(id);
    }

    @PostMapping("/manage/{id}/confirm")
    public OrderResponse confirm(@PathVariable UUID id,
                                 @Valid @RequestBody(required = false) UpdateStatusRequest request,
                                 Authentication authentication) {
        return orderService.confirm(id, userId(authentication), noteOf(request));
    }

    @PostMapping("/manage/{id}/ship")
    public OrderResponse ship(@PathVariable UUID id,
                              @Valid @RequestBody(required = false) UpdateStatusRequest request,
                              Authentication authentication) {
        return orderService.ship(id, userId(authentication), noteOf(request));
    }

    @PostMapping("/manage/{id}/complete")
    public OrderResponse complete(@PathVariable UUID id,
                                  @Valid @RequestBody(required = false) UpdateStatusRequest request,
                                  Authentication authentication) {
        return orderService.complete(id, userId(authentication), noteOf(request));
    }

    @PostMapping("/manage/{id}/cancel")
    public OrderResponse cancelAsStaff(@PathVariable UUID id,
                                       @Valid @RequestBody CancelOrderRequest request,
                                       Authentication authentication) {
        return orderService.cancel(id, userId(authentication), true, request.reason());
    }

    // ---------- Đường dẫn chung, đặt SAU /manage ----------

    @GetMapping("/{id}")
    public OrderResponse getMyOrder(@PathVariable UUID id, Authentication authentication) {
        return orderService.getMyOrder(userId(authentication), id);
    }

    @GetMapping("/{id}/history")
    public List<OrderStatusHistoryResponse> history(@PathVariable UUID id, Authentication authentication) {
        return orderService.getHistory(id, userId(authentication), isStaff(authentication));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelAsCustomer(@PathVariable UUID id,
                                          @Valid @RequestBody CancelOrderRequest request,
                                          Authentication authentication) {
        return orderService.cancel(id, userId(authentication), isStaff(authentication), request.reason());
    }

    private UUID userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).userId();
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String noteOf(UpdateStatusRequest request) {
        return request == null ? null : request.note();
    }
}
