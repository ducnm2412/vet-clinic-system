package com.vetclinic.pet.service;

import com.vetclinic.pet.domain.Medication;
import com.vetclinic.pet.dto.MedicationRequest;
import com.vetclinic.pet.dto.MedicationResponse;
import com.vetclinic.pet.exception.DuplicateMedicationException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import com.vetclinic.pet.repository.MedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Danh mục thuốc dùng chung. Bác sĩ và ADMIN đều quản lý được (quyết định của phòng khám), nên
 * service không phân biệt người gọi — việc chặn người ngoài nằm ở controller.
 */
@Service
@RequiredArgsConstructor
public class MedicationService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final MedicationRepository medicationRepository;

    @Transactional(readOnly = true)
    public List<MedicationResponse> search(String query, boolean includeInactive, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        String escaped = TextNormalizer.escapeLike(TextNormalizer.normalize(query));
        return medicationRepository.search(escaped, includeInactive, PageRequest.of(0, size))
                .stream().map(MedicationResponse::from).toList();
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
