package com.vetclinic.profile.service;

import com.vetclinic.profile.domain.PetGender;
import com.vetclinic.profile.dto.AddressRequest;
import com.vetclinic.profile.dto.AddressResponse;
import com.vetclinic.profile.dto.CustomerProfileRequest;
import com.vetclinic.profile.dto.CustomerProfileResponse;
import com.vetclinic.profile.dto.PetRequest;
import com.vetclinic.profile.dto.PetResponse;
import com.vetclinic.profile.exception.ResourceNotFoundException;
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

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class CustomerProfileServiceTest {

    @Autowired
    private CustomerProfileService customerProfileService;

    @Test
    void getMyProfile_lazyCreatesProfile() {
        UUID userId = UUID.randomUUID();

        CustomerProfileResponse response = customerProfileService.getMyProfile(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.phone()).isNull();
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void updateMyProfile_updatesFields() {
        UUID userId = UUID.randomUUID();

        CustomerProfileResponse response = customerProfileService.updateMyProfile(
                userId, new CustomerProfileRequest("0901234567", LocalDate.of(1995, 5, 20)));

        assertThat(response.phone()).isEqualTo("0901234567");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1995, 5, 20));
    }

    @Test
    void createAddress_secondDefault_clearsFirstDefault() {
        UUID userId = UUID.randomUUID();

        AddressResponse first = customerProfileService.createAddress(userId,
                new AddressRequest("123 A St", null, null, "HCM", true));
        customerProfileService.createAddress(userId,
                new AddressRequest("456 B St", null, null, "HCM", true));

        List<AddressResponse> addresses = customerProfileService.listAddresses(userId);
        long defaultCount = addresses.stream().filter(AddressResponse::isDefault).count();

        assertThat(defaultCount).isEqualTo(1);
        assertThat(addresses.stream().filter(a -> a.id().equals(first.id())).findFirst().orElseThrow().isDefault())
                .isFalse();
    }

    @Test
    void updateAddress_wrongOwner_throws() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        AddressResponse address = customerProfileService.createAddress(owner,
                new AddressRequest("123 A St", null, null, "HCM", false));

        assertThatThrownBy(() -> customerProfileService.updateAddress(intruder, address.id(),
                new AddressRequest("hacked", null, null, "XX", false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAddress_removesIt() {
        UUID userId = UUID.randomUUID();
        AddressResponse address = customerProfileService.createAddress(userId,
                new AddressRequest("123 A St", null, null, "HCM", false));

        customerProfileService.deleteAddress(userId, address.id());

        assertThat(customerProfileService.listAddresses(userId)).isEmpty();
    }

    @Test
    void createPet_thenUpdateThenDelete() {
        UUID userId = UUID.randomUUID();

        PetResponse created = customerProfileService.createPet(userId,
                new PetRequest("Milo", "Dog", "Poodle", PetGender.MALE, null, BigDecimal.valueOf(5.5)));
        assertThat(customerProfileService.listPets(userId)).hasSize(1);

        PetResponse updated = customerProfileService.updatePet(userId, created.id(),
                new PetRequest("Milo Updated", "Dog", null, null, null, BigDecimal.valueOf(6.0)));
        assertThat(updated.name()).isEqualTo("Milo Updated");
        assertThat(updated.weightKg()).isEqualByComparingTo("6.0");

        customerProfileService.deletePet(userId, created.id());
        assertThat(customerProfileService.listPets(userId)).isEmpty();
    }

    @Test
    void getProfileById_found_returnsProfile() {
        UUID userId = UUID.randomUUID();
        CustomerProfileResponse created = customerProfileService.updateMyProfile(userId,
                new CustomerProfileRequest("0909000000", LocalDate.of(1990, 1, 1)));

        CustomerProfileResponse found = customerProfileService.getProfileById(created.id());

        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.phone()).isEqualTo("0909000000");
    }

    @Test
    void getProfileById_unknownId_throws() {
        assertThatThrownBy(() -> customerProfileService.getProfileById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletePet_wrongOwner_throws() {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        PetResponse pet = customerProfileService.createPet(owner,
                new PetRequest("Milo", "Dog", null, null, null, null));

        assertThatThrownBy(() -> customerProfileService.deletePet(intruder, pet.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
