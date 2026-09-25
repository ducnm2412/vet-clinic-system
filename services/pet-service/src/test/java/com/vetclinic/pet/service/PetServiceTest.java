package com.vetclinic.pet.service;

import com.vetclinic.pet.domain.PetGender;
import com.vetclinic.pet.dto.PetRequest;
import com.vetclinic.pet.dto.PetResponse;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CN-09 → CN-12. Transaction rollback sau mỗi test, chạy trên pet_db_test (VD-12). */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class PetServiceTest {

    @Autowired private PetService petService;

    private PetRequest milo() {
        return new PetRequest("  Milo  ", " Chó ", "Poodle", PetGender.MALE,
                LocalDate.of(2023, 4, 1), BigDecimal.valueOf(5.5), "  Dị ứng Penicillin  ", "  ");
    }

    @Test
    void createThenListThenUpdateThenDelete() {
        UUID owner = UUID.randomUUID();

        PetResponse created = petService.create(owner, milo());
        // Tên nhập kèm khoảng trắng hai đầu — lưu bản đã cắt, không để "Milo " và "Milo" thành hai con.
        assertThat(created.name()).isEqualTo("Milo");
        assertThat(created.species()).isEqualTo("Chó");
        assertThat(created.ownerUserId()).isEqualTo(owner);
        // VD-22: dị ứng cắt khoảng trắng hai đầu; ô để trống thành null chứ không phải chuỗi rỗng —
        // "chưa khai" khác hẳn "đã khai là không có gì".
        assertThat(created.allergies()).isEqualTo("Dị ứng Penicillin");
        assertThat(created.notes()).isNull();
        assertThat(petService.listMine(owner)).hasSize(1);

        PetResponse updated = petService.update(owner, created.id(),
                new PetRequest("Milo lớn", "Chó", null, PetGender.FEMALE, null, BigDecimal.valueOf(7), null, "Sợ tiếng ồn"));
        assertThat(updated.name()).isEqualTo("Milo lớn");
        assertThat(updated.breed()).isNull();
        assertThat(updated.allergies()).isNull();
        assertThat(updated.notes()).isEqualTo("Sợ tiếng ồn");

        petService.delete(owner, created.id());
        assertThat(petService.listMine(owner)).isEmpty();
    }

    @Test
    void otherOwnersPetLooksLikeItDoesNotExist() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        PetResponse pet = petService.create(owner, milo());

        // 404 chứ không phải 403: 403 tự xác nhận con vật đó có thật.
        assertThatThrownBy(() -> petService.getMine(intruder, pet.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> petService.update(intruder, pet.id(), milo()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> petService.delete(intruder, pet.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(petService.listMine(owner)).hasSize(1);
    }

    @Test
    void clinicStaffSeeAnyPetButOnlyTheOwnersAsked() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID ownerC = UUID.randomUUID();
        PetResponse a = petService.create(ownerA, milo());
        petService.create(ownerB, new PetRequest("Bông", "Mèo", null, null, null, null, null, null));
        petService.create(ownerC, new PetRequest("Đen", "Mèo", null, null, null, null, null, null));

        assertThat(petService.getById(a.id()).name()).isEqualTo("Milo");
        assertThat(petService.list(List.of(a.id()))).hasSize(1);

        assertThat(petService.listByOwners(List.of(ownerA, ownerB)))
                .extracting(PetResponse::name)
                .containsExactlyInAnyOrder("Milo", "Bông");
        assertThat(petService.listByOwners(List.of())).isEmpty();
    }

    @Test
    void deletedAccountLosesItsPetsOnly() {
        UUID leaving = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        petService.create(leaving, milo());
        petService.create(leaving, new PetRequest("Bông", "Mèo", null, null, null, null, null, null));
        petService.create(staying, new PetRequest("Đen", "Mèo", null, null, null, null, null, null));

        petService.deleteAllOf(leaving);

        assertThat(petService.listMine(leaving)).isEmpty();
        assertThat(petService.listMine(staying)).hasSize(1);
    }

    @Test
    void unknownPetIsNotFound() {
        assertThatThrownBy(() -> petService.getById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
