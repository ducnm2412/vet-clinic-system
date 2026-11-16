package com.vetclinic.product.messaging;

import com.vetclinic.product.service.MedicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** Trừ kho thuốc khi phiếu thu đơn thuốc đã thu tiền. Không bao giờ chặn việc thu tiền (nghe bất đồng bộ). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionDispensedListener {

    private final MedicationService medicationService;

    @RabbitListener(queues = RabbitMQConfig.PRESCRIPTION_DISPENSED_QUEUE)
    public void onPrescriptionDispensed(PrescriptionDispensedEvent event) {
        if (event.paymentId() == null || event.lines() == null || event.lines().isEmpty()) {
            return;
        }
        List<MedicationService.DispensedLine> lines = event.lines().stream()
                .filter(l -> l.medicationName() != null && l.quantity() != null)
                .map(l -> new MedicationService.DispensedLine(l.medicationName(), l.quantity()))
                .toList();
        int applied = medicationService.applyDispense(event.paymentId(), lines);
        log.info("Phiếu thu {}: đã trừ kho {} loại thuốc", event.paymentId(), applied);
    }
}
