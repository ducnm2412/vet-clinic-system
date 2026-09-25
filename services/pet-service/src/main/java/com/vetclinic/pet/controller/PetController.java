package com.vetclinic.pet.controller;

import com.vetclinic.pet.dto.MedicalRecordResponse;
import com.vetclinic.pet.dto.PetRequest;
import com.vetclinic.pet.dto.PetResponse;
import com.vetclinic.pet.service.MedicalRecordService;
import com.vetclinic.pet.security.jwt.AuthenticatedUser;
import com.vetclinic.pet.service.PetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CN-09 → CN-12.
 *
 * Hai nhánh tách hẳn nhau: {@code /pets/me/**} là thú cưng của chính khách (chủ nuôi lấy từ
 * token, không nhận từ client), còn {@code /pets/**} là tra cứu của người trong phòng khám.
 * Khai literal "me" trước mẫu "{id}" nên Spring không bắt nhầm "me" thành một UUID.
 */
@RestController
@RequestMapping("/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final MedicalRecordService medicalRecordService;

    // ---------- khách tự quản lý thú cưng của mình ----------

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<PetResponse> listMine(@AuthenticationPrincipal AuthenticatedUser me) {
        return petService.listMine(me.userId());
    }

    @GetMapping("/me/{petId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PetResponse getMine(@PathVariable UUID petId, @AuthenticationPrincipal AuthenticatedUser me) {
        return petService.getMine(me.userId(), petId);
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public PetResponse create(@Valid @RequestBody PetRequest request,
                              @AuthenticationPrincipal AuthenticatedUser me) {
        return petService.create(me.userId(), request);
    }

    @PutMapping("/me/{petId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PetResponse update(@PathVariable UUID petId, @Valid @RequestBody PetRequest request,
                              @AuthenticationPrincipal AuthenticatedUser me) {
        return petService.update(me.userId(), petId, request);
    }

    @DeleteMapping("/me/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CUSTOMER')")
    public void delete(@PathVariable UUID petId, @AuthenticationPrincipal AuthenticatedUser me) {
        petService.delete(me.userId(), petId);
    }

    /** CN-24: lịch sử khám của con vật mình nuôi, mới nhất trước. */
    @GetMapping("/me/{petId}/medical-records")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<MedicalRecordResponse> myPetHistory(@PathVariable UUID petId,
                                                   @AuthenticationPrincipal AuthenticatedUser me) {
        return medicalRecordService.historyOfMyPet(me.userId(), petId);
    }

    // ---------- tra cứu trong phòng khám ----------

    /** Thú cưng của nhiều chủ một lượt — trang Khách hàng của admin dùng. */
    @GetMapping("/by-owners")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public List<PetResponse> byOwners(@RequestParam List<UUID> ownerUserIds) {
        return petService.listByOwners(ownerUserIds);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public List<PetResponse> list(@RequestParam(required = false) List<UUID> ids) {
        return petService.list(ids);
    }

    @GetMapping("/{petId}")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public PetResponse getById(@PathVariable UUID petId) {
        return petService.getById(petId);
    }

    /** CN-24: toàn bộ lượt khám trước đây của một con vật — bác sĩ cần trước khi khám tiếp. */
    @GetMapping("/{petId}/medical-records")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public List<MedicalRecordResponse> history(@PathVariable UUID petId) {
        return medicalRecordService.historyOfPet(petId);
    }
}
