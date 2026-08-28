package com.vetclinic.profile.service;

import com.vetclinic.profile.domain.Pet;
import com.vetclinic.profile.dto.PetResponse;
import com.vetclinic.profile.exception.ResourceNotFoundException;
import com.vetclinic.profile.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Tra cứu pet theo id, dùng cho doctor/staff/admin (vd: booking-service enrich thông tin pet
// khi hiển thị lịch hẹn) — khác với CustomerProfileService.listPets/... vốn chỉ cho chủ pet tự xem.
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;

    @Transactional(readOnly = true)
    public PetResponse getPetById(UUID id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found: " + id));
        return toPetResponse(pet);
    }

    @Transactional(readOnly = true)
    public List<PetResponse> getPetsByIds(List<UUID> ids) {
        return petRepository.findAllByIdIn(ids).stream()
                .map(this::toPetResponse)
                .toList();
    }

    private PetResponse toPetResponse(Pet pet) {
        return new PetResponse(pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(), pet.getGender(),
                pet.getDateOfBirth(), pet.getWeightKg(), pet.getCreatedAt(), pet.getUpdatedAt());
    }
}
