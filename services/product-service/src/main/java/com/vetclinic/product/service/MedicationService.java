package com.vetclinic.product.service;

import com.vetclinic.product.domain.Medication;
import com.vetclinic.product.domain.MedicationStockMovement;
import com.vetclinic.product.domain.StockMovementType;
import com.vetclinic.product.dto.MedicationRequest;
import com.vetclinic.product.dto.MedicationResponse;
import com.vetclinic.product.dto.MedicationStockMovementResponse;
import com.vetclinic.product.dto.StockAdjustmentRequest;
import com.vetclinic.product.exception.DuplicateMedicationException;
import com.vetclinic.product.exception.InsufficientStockException;
import com.vetclinic.product.exception.ResourceNotFoundException;
import com.vetclinic.product.repository.MedicationRepository;
import com.vetclinic.product.repository.MedicationStockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Danh mục thuốc dùng chung. Bác sĩ và ADMIN đều quản lý được (quyết định của phòng khám), nên
 * service không phân biệt người gọi — việc chặn người ngoài nằm ở controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    static final int INVENTORY_LIMIT = 500;

    private final MedicationRepository medicationRepository;
    private final MedicationStockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<MedicationResponse> search(String query, boolean includeInactive, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        String escaped = TextNormalizer.escapeLike(TextNormalizer.normalize(query));
        return medicationRepository.search(escaped, includeInactive, PageRequest.of(0, size))
                .stream().map(MedicationResponse::from).toList();
    }

    /**
     * Giá trong danh mục của các thuốc bác sĩ vừa gõ, để pet-service ghi vào phiếu thu. Đơn lưu tên
     * thuốc dạng chữ nên khớp theo tên đã bỏ dấu như lúc tìm gợi ý. Khoá trả về là đúng chuỗi người
     * gọi gửi lên; thuốc ngoài danh mục hoặc chưa có giá thì không có mặt trong kết quả.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> pricesByName(List<String> names) {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String name : names) {
            medicationRepository.findBySearchName(TextNormalizer.normalize(name))
                    .map(Medication::getUnitPrice)
                    .ifPresent(price -> prices.put(name, price));
        }
        return prices;
    }

    @Transactional
    public MedicationResponse create(MedicationRequest request) {
        String name = request.name().trim();
        String searchName = TextNormalizer.normalize(name);
        if (medicationRepository.existsBySearchName(searchName)) {
            throw new DuplicateMedicationException("Thuốc \"" + name + "\" đã có trong danh mục");
        }
        Medication medication = Medication.builder()
                .name(name)
                .searchName(searchName)
                .unit(blankToNull(request.unit()))
                .caution(blankToNull(request.caution()))
                .unitPrice(request.unitPrice())
                .active(request.active() == null || request.active())
                .build();
        return MedicationResponse.from(medicationRepository.save(medication));
    }

    @Transactional
    public MedicationResponse update(UUID id, MedicationRequest request) {
        Medication medication = find(id);
        String name = request.name().trim();
        String searchName = TextNormalizer.normalize(name);
        if (medicationRepository.existsBySearchNameAndIdNot(searchName, id)) {
            throw new DuplicateMedicationException("Thuốc \"" + name + "\" đã có trong danh mục");
        }
        medication.setName(name);
        medication.setSearchName(searchName);
        medication.setUnit(blankToNull(request.unit()));
        medication.setCaution(blankToNull(request.caution()));
        medication.setUnitPrice(request.unitPrice());
        if (request.active() != null) {
            medication.setActive(request.active());
        }
        return MedicationResponse.from(medicationRepository.save(medication));
    }

    /**
     * "Xoá" là ẩn khỏi gợi ý. Không xoá cứng: đơn mẫu sau này trỏ vào thuốc, và gọi lại nhiều lần
     * không lỗi.
     */
    @Transactional
    public void deactivate(UUID id) {
        Medication medication = find(id);
        medication.setActive(false);
        medicationRepository.save(medication);
    }

    // ---------- Kho thuốc ----------

    /** Danh sách cho màn kho, kèm tồn. {@code limit} trống = tối đa {@value #INVENTORY_LIMIT} dòng. */
    @Transactional(readOnly = true)
    public List<MedicationResponse> inventory(String query, boolean includeInactive, boolean lowOnly) {
        String escaped = TextNormalizer.escapeLike(TextNormalizer.normalize(query));
        return medicationRepository.inventory(escaped, includeInactive, lowOnly, PageRequest.of(0, INVENTORY_LIMIT))
                .stream().map(MedicationResponse::from).toList();
    }

    /** Nhập kho / điều chỉnh tay. Không cho tồn xuống dưới 0 (khác với phát thuốc, xem {@link #applyDispense}). */
    @Transactional
    public MedicationResponse adjustStock(UUID id, StockAdjustmentRequest request, UUID actorUserId) {
        Medication medication = findForUpdate(id);
        int after = medication.getStockQuantity() + request.quantityChange();
        if (after < 0) {
            throw new InsufficientStockException(medication.getName(),
                    medication.getUnit() == null ? "" : medication.getUnit(),
                    medication.getStockQuantity(), -request.quantityChange());
        }
        medication.setStockQuantity(after);
        recordMovement(medication, request.type(), request.quantityChange(), 0, request.note(), null, actorUserId);
        return MedicationResponse.from(medication);
    }

    @Transactional
    public MedicationResponse setLowStockThreshold(UUID id, int threshold) {
        Medication medication = find(id);
        medication.setLowStockThreshold(threshold);
        return MedicationResponse.from(medicationRepository.save(medication));
    }

    @Transactional(readOnly = true)
    public List<MedicationStockMovementResponse> movements(UUID id) {
        find(id);
        return movementRepository.findByMedicationIdOrderByCreatedAtDesc(id).stream()
                .map(MedicationStockMovementResponse::from).toList();
    }

    /** Một dòng thuốc của phiếu thu đã trả tiền. {@code name} là tên chữ ghi trên đơn. */
    public record DispensedLine(String name, int quantity) {
    }

    /**
     * Trừ kho theo một phiếu thu đã thu tiền. KHÔNG chặn: khách đã nhận thuốc rồi, nên kho thiếu thì
     * tồn về 0 và phần thiếu ghi vào {@code shortfall} của dòng lịch sử. Thuốc ngoài danh mục thì bỏ
     * qua (đơn lưu tên chữ nên không biết là thuốc nào). Gọi lại với cùng {@code paymentId} (RabbitMQ
     * giao lại) không trừ thêm lần nào.
     *
     * Cùng một thuốc xuất hiện ở nhiều dòng của phiếu thì gộp số lượng: lịch sử chỉ có một dòng cho
     * mỗi (thuốc, phiếu thu).
     *
     * @return số thuốc đã trừ lần này (không tính thuốc ngoài danh mục và thuốc đã trừ trước đó)
     */
    @Transactional
    public int applyDispense(UUID paymentId, List<DispensedLine> lines) {
        Map<UUID, Integer> quantityByMedication = new LinkedHashMap<>();
        for (DispensedLine line : lines) {
            if (line.quantity() <= 0) {
                continue;
            }
            Optional<Medication> found = medicationRepository.findBySearchName(TextNormalizer.normalize(line.name()));
            if (found.isEmpty()) {
                log.info("Phiếu thu {}: thuốc \"{}\" không có trong danh mục, không trừ kho", paymentId, line.name());
                continue;
            }
            quantityByMedication.merge(found.get().getId(), line.quantity(), Integer::sum);
        }

        int applied = 0;
        // Khoá theo thứ tự id để hai phiếu thu cùng lúc không khoá chéo nhau thành deadlock.
        for (UUID medicationId : quantityByMedication.keySet().stream().sorted().toList()) {
            if (movementRepository.existsByMedicationIdAndReferenceIdAndType(
                    medicationId, paymentId, StockMovementType.SALE)) {
                log.info("Bỏ qua sự kiện trùng: phiếu thu {} đã trừ kho thuốc {}", paymentId, medicationId);
                continue;
            }
            Medication medication = findForUpdate(medicationId);
            int requested = quantityByMedication.get(medicationId);
            int taken = Math.min(requested, medication.getStockQuantity());
            int shortfall = requested - taken;
            medication.setStockQuantity(medication.getStockQuantity() - taken);
            String note = shortfall == 0
                    ? "Phát thuốc theo phiếu thu"
                    : "Phát thuốc theo phiếu thu, kho thiếu " + shortfall;
            recordMovement(medication, StockMovementType.SALE, -taken, shortfall, note, paymentId, null);
            if (shortfall > 0) {
                log.warn("Phiếu thu {}: thuốc \"{}\" kho chỉ còn {} nhưng đã phát {}, thiếu {}",
                        paymentId, medication.getName(), taken, requested, shortfall);
            }
            applied++;
        }
        return applied;
    }

    private void recordMovement(Medication medication, StockMovementType type, int change, int shortfall,
                                String note, UUID referenceId, UUID actorUserId) {
        movementRepository.save(MedicationStockMovement.builder()
                .medication(medication)
                .type(type)
                .quantityChange(change)
                .quantityAfter(medication.getStockQuantity())
                .shortfall(shortfall)
                .note(note)
                .referenceId(referenceId)
                .createdBy(actorUserId)
                .build());
    }

    private Medication findForUpdate(UUID id) {
        return medicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thuốc " + id));
    }

    private Medication find(UUID id) {
        return medicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thuốc " + id));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
