package com.vetclinic.payment.controller;

import com.vetclinic.payment.dto.PaymentResponse;
import com.vetclinic.payment.dto.SetAmountRequest;
import com.vetclinic.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payment/payments/pending")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<PaymentResponse> listPending() {
        return paymentService.listPending();
    }

    @PutMapping("/payment/payments/{id}/amount")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PaymentResponse setAmount(@PathVariable UUID id, @Valid @RequestBody SetAmountRequest request) {
        return paymentService.setAmount(id, request.amount());
    }

    @PutMapping("/payment/payments/{id}/confirm-cash")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PaymentResponse confirmCash(@PathVariable UUID id) {
        return paymentService.confirmCash(id);
    }
}
