package com.vetclinic.product.service;

import com.vetclinic.product.domain.StockMovementType;
import com.vetclinic.product.dto.MedicationRequest;
import com.vetclinic.product.dto.MedicationResponse;
import com.vetclinic.product.dto.MedicationStockMovementResponse;
import com.vetclinic.product.dto.StockAdjustmentRequest;
import com.vetclinic.product.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Kho thuốc: nhập/điều chỉnh, phát thuốc theo phiếu thu (không chặn, ghi phần thiếu, chống trừ trùng). */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@Transactional
class MedicationStockTest {

    private static final UUID STAFF = UUID.randomUUID();

    @Autowired private MedicationService medicationService;

    private MedicationResponse add(String name) {
        return medicationService.create(new MedicationRequest(name, "viên", null, null));
    }

    private MedicationResponse stock(UUID id, StockMovementType type, int change) {
        return medicationService.adjustStock(id, new StockAdjustmentRequest(type, change, "test"), STAFF);
    }

    private int stockOf(String name) {
        return medicationService.inventory(name, false, false).get(0).stockQuantity();
    }

    @Test
    void newMedicationStartsAtZeroStockAndZeroThreshold() {
        MedicationResponse m = add("Kho Test A");
        assertThat(m.stockQuantity()).isZero();
        assertThat(m.lowStockThreshold()).isZero();
    }

    @Test
    void importAndAdjustmentChangeStockAndLeaveAHistory() {
        UUID id = add("Kho Test B").id();

        stock(id, StockMovementType.IMPORT, 100);
        MedicationResponse after = stock(id, StockMovementType.ADJUSTMENT, -30);

        assertThat(after.stockQuantity()).isEqualTo(70);
        List<MedicationStockMovementResponse> history = medicationService.movements(id);
        assertThat(history).hasSize(2);
        assertThat(history).extracting(MedicationStockMovementResponse::quantityAfter).containsExactlyInAnyOrder(100, 70);
        assertThat(history).allSatisfy(h -> assertThat(h.createdBy()).isEqualTo(STAFF));
    }

    @Test
    void manualAdjustmentCannotTakeStockBelowZero() {
        UUID id = add("Kho Test C").id();
        stock(id, StockMovementType.IMPORT, 5);

        assertThatThrownBy(() -> stock(id, StockMovementType.ADJUSTMENT, -6))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(stockOf("Kho Test C")).isEqualTo(5);
    }

    @Test
    void dispenseDeductsStock_matchingNameIgnoringCaseAndDiacritics() {
        UUID id = add("Kho Thuốc D").id();
        stock(id, StockMovementType.IMPORT, 50);
        UUID payment = UUID.randomUUID();

        int applied = medicationService.applyDispense(payment,
                List.of(new MedicationService.DispensedLine("kho thuoc d", 16)));

        assertThat(applied).isEqualTo(1);
        assertThat(stockOf("Kho Thuốc D")).isEqualTo(34);
        MedicationStockMovementResponse sale = medicationService.movements(id).stream()
                .filter(m -> m.type() == StockMovementType.SALE).findFirst().orElseThrow();
        assertThat(sale.quantityChange()).isEqualTo(-16);
        assertThat(sale.shortfall()).isZero();
        assertThat(sale.referenceId()).isEqualTo(payment);
    }

    @Test
    void dispenseNeverBlocks_shortStockGoesToZeroAndRecordsTheShortfall() {
        UUID id = add("Kho Test E").id();
        stock(id, StockMovementType.IMPORT, 3);

        medicationService.applyDispense(UUID.randomUUID(), List.of(new MedicationService.DispensedLine("Kho Test E", 10)));

        assertThat(stockOf("Kho Test E")).isZero();
        MedicationStockMovementResponse sale = medicationService.movements(id).stream()
                .filter(m -> m.type() == StockMovementType.SALE).findFirst().orElseThrow();
        assertThat(sale.quantityChange()).isEqualTo(-3);
        assertThat(sale.shortfall()).isEqualTo(7);
    }

    @Test
    void dispenseOnEmptyStockStillSucceedsAndRecordsTheWholeQuantityAsShortfall() {
        UUID id = add("Kho Test F").id();

        medicationService.applyDispense(UUID.randomUUID(), List.of(new MedicationService.DispensedLine("Kho Test F", 4)));

        assertThat(stockOf("Kho Test F")).isZero();
        assertThat(medicationService.movements(id)).singleElement().satisfies(m -> {
            assertThat(m.quantityChange()).isZero();
            assertThat(m.shortfall()).isEqualTo(4);
        });
    }

    @Test
    void redeliveredEventDoesNotDeductTwice() {
        UUID id = add("Kho Test G").id();
        stock(id, StockMovementType.IMPORT, 20);
        UUID payment = UUID.randomUUID();
        List<MedicationService.DispensedLine> lines = List.of(new MedicationService.DispensedLine("Kho Test G", 5));

        assertThat(medicationService.applyDispense(payment, lines)).isEqualTo(1);
        assertThat(medicationService.applyDispense(payment, lines)).isZero();

        assertThat(stockOf("Kho Test G")).isEqualTo(15);
    }

    @Test
    void sameMedicationOnTwoLinesIsMergedIntoOneMovement_andUnknownNamesAreSkipped() {
        UUID id = add("Kho Test H").id();
        stock(id, StockMovementType.IMPORT, 30);

        int applied = medicationService.applyDispense(UUID.randomUUID(), List.of(
                new MedicationService.DispensedLine("Kho Test H", 4),
                new MedicationService.DispensedLine("kho test h", 6),
                new MedicationService.DispensedLine("Thuoc Khong Co Trong Danh Muc", 9),
                new MedicationService.DispensedLine("Kho Test H", 0)));

        assertThat(applied).isEqualTo(1);
        assertThat(stockOf("Kho Test H")).isEqualTo(20);
        assertThat(medicationService.movements(id).stream().filter(m -> m.type() == StockMovementType.SALE)).hasSize(1);
    }

    @Test
    void lowStockFilterListsOutOfStockAndBelowThresholdButNotHealthyOrHiddenOnes() {
        UUID low = add("Kho Low Test 1").id();
        UUID healthy = add("Kho Low Test 2").id();
        UUID hidden = add("Kho Low Test 3").id();
        stock(low, StockMovementType.IMPORT, 3);
        medicationService.setLowStockThreshold(low, 5);
        stock(healthy, StockMovementType.IMPORT, 50);
        medicationService.setLowStockThreshold(healthy, 5);
        medicationService.deactivate(hidden);

        List<String> names = medicationService.inventory("kho low test", false, true).stream()
                .map(MedicationResponse::name).toList();

        assertThat(names).containsExactly("Kho Low Test 1");
    }

    @Test
    void thresholdCanBeChanged() {
        UUID id = add("Kho Test I").id();
        assertThat(medicationService.setLowStockThreshold(id, 12).lowStockThreshold()).isEqualTo(12);
    }
}
