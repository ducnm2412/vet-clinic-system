package com.vetclinic.pet.service;

import com.vetclinic.pet.domain.Pet;
import com.vetclinic.pet.dto.PetRequest;
import com.vetclinic.pet.dto.PetResponse;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import com.vetclinic.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return toResponse(petRepository.saveAndFlush(pet));
    }

    @Transactional
    public void delete(UUID ownerUserId, UUID petId) {
        petRepository.delete(ownedOrThrow(ownerUserId, petId));
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

    /** Tài khoản khách bị xoá thì hồ sơ thú cưng đi theo — trước đây do khoá ngoại của profile_db lo. */
    @Transactional
    public void deleteAllOf(UUID ownerUserId) {
        petRepository.deleteByOwnerUserId(ownerUserId);
        log.info("Đã xoá hồ sơ thú cưng của tài khoản {} vừa bị xoá", ownerUserId);
    }

    private Pet ownedOrThrow(UUID ownerUserId, UUID petId) {
        return petRepository.findByIdAndOwnerUserId(petId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thú cưng: " + petId));
    }

    private static PetResponse toResponse(Pet pet) {
        return new PetResponse(pet.getId(), pet.getOwnerUserId(), pet.getName(), pet.getSpecies(), pet.getBreed(),
                pet.getGender(), pet.getDateOfBirth(), pet.getWeightKg(), pet.getCreatedAt(), pet.getUpdatedAt());
    }
}
