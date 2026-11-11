package com.vetclinic.pet.service;

import com.vetclinic.pet.domain.Medication;
import com.vetclinic.pet.domain.PrescriptionTemplate;
import com.vetclinic.pet.domain.PrescriptionTemplateItem;
import com.vetclinic.pet.dto.PrescriptionTemplateItemRequest;
import com.vetclinic.pet.dto.PrescriptionTemplateRequest;
import com.vetclinic.pet.dto.PrescriptionTemplateResponse;
import com.vetclinic.pet.dto.PrescriptionTemplateSummary;
import com.vetclinic.pet.exception.DuplicateTemplateException;
import com.vetclinic.pet.exception.InvalidTemplateException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import com.vetclinic.pet.repository.MedicationRepository;
import com.vetclinic.pet.repository.PrescriptionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Đơn mẫu theo bệnh, dùng chung cả phòng khám: bác sĩ và ADMIN đều tạo, sửa, xoá được (chặn người
 * ngoài ở controller). Mẫu chỉ điền sẵn vào form bệnh án — service này không đụng tới bệnh án.
 */
@Service
@RequiredArgsConstructor
public class PrescriptionTemplateService {

    private final PrescriptionTemplateRepository templateRepository;
    private final MedicationRepository medicationRepository;

    @Transactional(readOnly = true)
    public List<PrescriptionTemplateSummary> search(String query) {
        String escaped = TextNormalizer.escapeLike(TextNormalizer.normalize(query));
        return templateRepository.search(escaped).stream().map(PrescriptionTemplateSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionTemplateResponse get(UUID id) {
        return PrescriptionTemplateResponse.from(find(id));
    }

    @Transactional
    public PrescriptionTemplateResponse create(UUID createdBy, PrescriptionTemplateRequest request) {
        String name = request.name().trim();
        String searchName = TextNormalizer.normalize(name);
        if (templateRepository.existsBySearchName(searchName)) {
            throw duplicate(name);
        }
        PrescriptionTemplate template = PrescriptionTemplate.builder().createdBy(createdBy).build();
        fill(template, name, searchName, request, Set.of());
        return PrescriptionTemplateResponse.from(templateRepository.saveAndFlush(template));
    }

    @Transactional
    public PrescriptionTemplateResponse update(UUID id, PrescriptionTemplateRequest request) {
        PrescriptionTemplate template = find(id);
        String name = request.name().trim();
        String searchName = TextNormalizer.normalize(name);
        if (templateRepository.existsBySearchNameAndIdNot(searchName, id)) {
            throw duplicate(name);
        }
        // Thuốc đã nằm sẵn trong mẫu thì được giữ dù sau đó bị ẩn khỏi danh mục — nếu không, bác sĩ
        // sửa chẩn đoán cũng bị chặn chỉ vì mẫu còn một thuốc đã ẩn.
        Set<UUID> alreadyInTemplate = new HashSet<>();
        template.getItems().forEach(item -> alreadyInTemplate.add(item.getMedication().getId()));
        fill(template, name, searchName, request, alreadyInTemplate);
        return PrescriptionTemplateResponse.from(templateRepository.saveAndFlush(template));
    }

    /** Xoá cứng: các dòng thuốc xoá theo (cascade), bệnh án đã kê không ảnh hưởng vì không trỏ về mẫu. */
    @Transactional
    public void delete(UUID id) {
        templateRepository.delete(find(id));
    }

    private PrescriptionTemplate find(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn mẫu " + id));
    }

    private void fill(PrescriptionTemplate template, String name, String searchName,
                      PrescriptionTemplateRequest request, Set<UUID> allowedInactive) {
        template.setName(name);
        template.setSearchName(searchName);
        template.setDiagnosis(request.diagnosis().trim());
        template.setTreatment(blankToNull(request.treatment()));
        template.setNotes(blankToNull(request.notes()));

        List<PrescriptionTemplateItemRequest> requested = request.items() == null ? List.of() : request.items();
        Map<UUID, Medication> medications = loadMedications(requested);

        // clear() + add() trên chính collection đang persistent để orphanRemoval xoá đúng các dòng
        // thuốc cũ — KHÔNG gán list mới, Hibernate sẽ mất tracking (giống MedicalRecordService).
        template.getItems().clear();
        for (int i = 0; i < requested.size(); i++) {
            PrescriptionTemplateItemRequest item = requested.get(i);
            Medication medication = medications.get(item.medicationId());
            if (medication == null) {
                throw new InvalidTemplateException("Thuốc " + item.medicationId() + " không có trong danh mục");
            }
            if (!medication.isActive() && !allowedInactive.contains(medication.getId())) {
                throw new InvalidTemplateException(
                        "Thuốc \"" + medication.getName() + "\" đã bị ẩn khỏi danh mục, không thêm vào mẫu được");
            }
            template.getItems().add(PrescriptionTemplateItem.builder()
                    .template(template)
                    .medication(medication)
                    .dosage(item.dosage().trim())
                    .frequency(item.frequency().trim())
                    .durationDays(item.durationDays())
                    .notes(blankToNull(item.notes()))
                    .sortOrder(i)
                    .build());
        }
    }

    private Map<UUID, Medication> loadMedications(List<PrescriptionTemplateItemRequest> requested) {
        Set<UUID> ids = new HashSet<>();
        requested.forEach(item -> ids.add(item.medicationId()));
        Map<UUID, Medication> byId = new HashMap<>();
        medicationRepository.findAllById(ids).forEach(m -> byId.put(m.getId(), m));
        return byId;
    }

    private static DuplicateTemplateException duplicate(String name) {
        return new DuplicateTemplateException("Đã có đơn mẫu tên \"" + name + "\"");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
