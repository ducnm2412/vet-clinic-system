package com.vetclinic.pet.service;

import com.vetclinic.pet.domain.Pet;
import com.vetclinic.pet.dto.PetRequest;
import com.vetclinic.pet.dto.PetResponse;
import com.vetclinic.pet.exception.PetHasMedicalRecordsException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import com.vetclinic.pet.repository.MedicalRecordRepository;
import com.vetclinic.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * CN-09 → CN-12: hồ sơ thú cưng.
 *
 * Khách chỉ đụng được thú cưng của chính mình: mọi thao tác đều tra theo (petId, ownerUserId),
 * không phải của mình thì trả "không tìm thấy" chứ không phải "không có quyền" — 403 là tự xác
 * nhận con vật đó có thật.
 *
 * Bác sĩ và nhân viên tra được mọi thú cưng, vì họ cần xem con vật đang khám là con nào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PetPhotoStorageService photoStorageService;

    // ---------- của chính khách ----------

    @Transactional(readOnly = true)
    public List<PetResponse> listMine(UUID ownerUserId) {
        return petRepository.findByOwnerUserIdOrderByCreatedAtAsc(ownerUserId).stream()
                .map(PetService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PetResponse getMine(UUID ownerUserId, UUID petId) {
        return toResponse(ownedOrThrow(ownerUserId, petId));
    }

    @Transactional
    public PetResponse create(UUID ownerUserId, PetRequest request) {
        Pet pet = petRepository.saveAndFlush(Pet.builder()
                .ownerUserId(ownerUserId)
                .name(request.name().trim())
                .species(request.species().trim())
                .breed(request.breed())
                .gender(request.gender())
                .dateOfBirth(request.dateOfBirth())
                .weightKg(request.weightKg())
                .allergies(trimToNull(request.allergies()))
                .notes(trimToNull(request.notes()))
                .build());
        log.info("Khách {} thêm thú cưng {} ({})", ownerUserId, pet.getName(), pet.getId());
        return toResponse(pet);
    }

    @Transactional
    public PetResponse update(UUID ownerUserId, UUID petId, PetRequest request) {
        Pet pet = ownedOrThrow(ownerUserId, petId);
        pet.setName(request.name().trim());
        pet.setSpecies(request.species().trim());
        pet.setBreed(request.breed());
        pet.setGender(request.gender());
        pet.setDateOfBirth(request.dateOfBirth());
        pet.setWeightKg(request.weightKg());
        pet.setAllergies(trimToNull(request.allergies()));
        pet.setNotes(trimToNull(request.notes()));
        return toResponse(petRepository.saveAndFlush(pet));
    }

    /** Ghi đè ảnh cũ nếu có. Chỉ chủ nuôi mới đổi được ảnh con của mình. */
    @Transactional
    public PetResponse uploadPhoto(UUID ownerUserId, UUID petId, MultipartFile file) {
        Pet pet = ownedOrThrow(ownerUserId, petId);
        photoStorageService.store(pet.getId(), file);
        pet.setPhotoVersion(pet.getPhotoVersion() + 1);
        return toResponse(petRepository.saveAndFlush(pet));
    }

    /**
     * Ảnh đọc được bởi chủ nuôi hoặc người trong phòng khám. {@code ownerUserId == null} nghĩa là
     * người gọi thuộc phòng khám (bác sĩ, nhân viên, quản trị): không giới hạn theo chủ.
     */
    @Transactional(readOnly = true)
    public PetPhotoStorageService.StoredPhoto loadPhoto(UUID petId, UUID ownerUserId) {
        Pet pet = ownerUserId == null
                ? petRepository.findById(petId).orElse(null)
                : petRepository.findByIdAndOwnerUserId(petId, ownerUserId).orElse(null);
        if (pet == null || pet.getPhotoVersion() == 0) {
            throw new ResourceNotFoundException("Không có ảnh cho thú cưng: " + petId);
        }
        return photoStorageService.load(petId);
    }

    /**
     * Đã có bệnh án thì không xoá được: bệnh án là hồ sơ lâm sàng của phòng khám, xoá con vật đi sẽ
     * để lại bệnh án không ai tra ra được nữa. Khoá ngoại ON DELETE RESTRICT là lưới an toàn cuối,
     * ở đây chặn trước để câu trả lời là 409 kèm lời giải thích thay vì lỗi database.
     */
    @Transactional
    public void delete(UUID ownerUserId, UUID petId) {
        Pet pet = ownedOrThrow(ownerUserId, petId);
        if (medicalRecordRepository.existsByPetId(petId)) {
            throw new PetHasMedicalRecordsException(pet.getName()
                    + " đã có bệnh án tại phòng khám nên không xoá được hồ sơ. Sửa lại thông tin nếu cần.");
        }
        petRepository.delete(pet);
        photoStorageService.delete(petId);
    }

    /**
     * CN-19: nhân viên lập hồ sơ hộ khách. Cùng một việc với {@link #create}, chỉ khác chỗ lấy
     * chủ nuôi — nên gọi thẳng vào đó thay vì chép lại.
     */
    @Transactional
    public PetResponse createFor(UUID ownerUserId, PetRequest request) {
        return create(ownerUserId, request);
    }

    // ---------- tra cứu cho người trong phòng khám ----------

    @Transactional(readOnly = true)
    public PetResponse getById(UUID petId) {
        return toResponse(petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thú cưng: " + petId)));
    }

    @Transactional(readOnly = true)
    public List<PetResponse> list(Collection<UUID> ids) {
        List<Pet> pets = (ids == null || ids.isEmpty()) ? petRepository.findAll() : petRepository.findByIdIn(ids);
        return pets.stream().map(PetService::toResponse).toList();
    }

    /** Cho trang Khách hàng của admin: thú cưng của nhiều chủ một lượt. */
    @Transactional(readOnly = true)
    public List<PetResponse> listByOwners(Collection<UUID> ownerUserIds) {
        if (ownerUserIds == null || ownerUserIds.isEmpty()) {
            return List.of();
        }
        return petRepository.findByOwnerUserIdInOrderByCreatedAtAsc(ownerUserIds).stream()
                .map(PetService::toResponse).toList();
    }

    /**
     * Tài khoản khách bị xoá thì hồ sơ thú cưng đi theo — trước đây do khoá ngoại của profile_db lo.
     *
     * Con vật đã có bệnh án thì giữ lại: xoá tài khoản là việc hành chính, không được kéo theo hồ sơ
     * lâm sàng của phòng khám. Những con đó thành hồ sơ không có chủ, vẫn tra được theo bệnh án.
     */
    @Transactional
    public void deleteAllOf(UUID ownerUserId) {
        List<Pet> pets = petRepository.findByOwnerUserIdOrderByCreatedAtAsc(ownerUserId);
        List<Pet> deletable = pets.stream().filter(pet -> !medicalRecordRepository.existsByPetId(pet.getId())).toList();
        petRepository.deleteAll(deletable);
        deletable.forEach(pet -> photoStorageService.delete(pet.getId()));
        int kept = pets.size() - deletable.size();
        if (kept > 0) {
            log.info("Giữ lại {} hồ sơ thú cưng của tài khoản {} vì đã có bệnh án", kept, ownerUserId);
        }
        log.info("Đã xoá {} hồ sơ thú cưng của tài khoản {} vừa bị xoá", deletable.size(), ownerUserId);
    }

    private Pet ownedOrThrow(UUID ownerUserId, UUID petId) {
        return petRepository.findByIdAndOwnerUserId(petId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thú cưng: " + petId));
    }

    /** Ô để trống gửi lên là chuỗi rỗng, không phải null — lưu thẳng sẽ thành "đã khai, khai rỗng". */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // URL dựng lại từ (id, version) lúc trả response, không lưu chuỗi trong DB. `?v=` chỉ để trình duyệt
    // bỏ cache khi ảnh được thay.
    private static String photoUrl(Pet pet) {
        return pet.getPhotoVersion() > 0 ? "/pets/photos/" + pet.getId() + "?v=" + pet.getPhotoVersion() : null;
    }

    private static PetResponse toResponse(Pet pet) {
        return new PetResponse(pet.getId(), pet.getOwnerUserId(), pet.getName(), pet.getSpecies(), pet.getBreed(),
                pet.getGender(), pet.getDateOfBirth(), pet.getWeightKg(), pet.getAllergies(), pet.getNotes(),
                photoUrl(pet), pet.getCreatedAt(), pet.getUpdatedAt());
    }
}
