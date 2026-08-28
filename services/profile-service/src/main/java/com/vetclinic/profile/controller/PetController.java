package com.vetclinic.profile.controller;

import com.vetclinic.profile.dto.PetResponse;
import com.vetclinic.profile.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Endpoint enrich cho doctor/staff/admin (vd: booking-service gọi qua Feign để lấy tên/loài pet
// khi hiển thị lịch hẹn) — không dành cho customer tự xem, customer đã có /profile/customer/me/pets.
@RestController
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping("/profile/pets/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public PetResponse getPetById(@PathVariable UUID id) {
        return petService.getPetById(id);
    }

    @GetMapping("/profile/pets")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public List<PetResponse> getPetsByIds(@RequestParam List<UUID> ids) {
        return petService.getPetsByIds(ids);
    }
}
