package com.vetclinic.product.controller;

import com.vetclinic.product.dto.LowStockThresholdRequest;
import com.vetclinic.product.dto.MedicationResponse;
import com.vetclinic.product.dto.MedicationStockMovementResponse;
import com.vetclinic.product.dto.StockAdjustmentRequest;
import com.vetclinic.product.security.jwt.AuthenticatedUser;
import com.vetclinic.product.service.MedicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Kho thuốc cho nhân viên quầy và ADMIN, giống kho sản phẩm. Tách khỏi MedicationController vì bên đó
 * dành cho bác sĩ và ADMIN (danh mục tra cứu), còn kho thì nhân viên mới là người nhập và kiểm.
 */
@RestController
@RequestMapping("/medications")
@PreAuthorize("hasAnyRole('STAFF','ADMIN')")
@RequiredArgsConstructor
public class MedicationStockController {

    private final MedicationService medicationService;

    /** Danh sách thuốc kèm tồn; {@code lowOnly=true} chỉ lấy thuốc sắp hết hoặc đã hết. */
    @GetMapping("/inventory")
    public List<MedicationResponse> inventory(@RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "false") boolean includeInactive,
                                              @RequestParam(defaultValue = "false") boolean lowOnly) {
        return medicationService.inventory(q, includeInactive, lowOnly);
    }

    @PostMapping("/{id}/stock")
    public MedicationResponse adjustStock(@PathVariable UUID id,
                                          @Valid @RequestBody StockAdjustmentRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser me) {
        return medicationService.adjustStock(id, request, me.userId());
    }

    @PutMapping("/{id}/low-stock-threshold")
    public MedicationResponse setLowStockThreshold(@PathVariable UUID id,
                                                   @Valid @RequestBody LowStockThresholdRequest request) {
        return medicationService.setLowStockThreshold(id, request.threshold());
    }

    @GetMapping("/{id}/stock-movements")
    public List<MedicationStockMovementResponse> movements(@PathVariable UUID id) {
        return medicationService.movements(id);
    }
}
