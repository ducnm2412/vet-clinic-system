package com.vetclinic.profile.controller;

import com.vetclinic.profile.dto.AddressRequest;
import com.vetclinic.profile.dto.AddressResponse;
import com.vetclinic.profile.dto.CustomerProfileRequest;
import com.vetclinic.profile.dto.CustomerProfileResponse;
import com.vetclinic.profile.dto.PetRequest;
import com.vetclinic.profile.dto.PetResponse;
import com.vetclinic.profile.security.jwt.AuthenticatedUser;
import com.vetclinic.profile.service.CustomerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/profile/customer/me")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    @GetMapping
    public CustomerProfileResponse getMyProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return customerProfileService.getMyProfile(principal.userId());
    }

    @PutMapping
    public CustomerProfileResponse updateMyProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody CustomerProfileRequest request) {
        return customerProfileService.updateMyProfile(principal.userId(), request);
    }

    @GetMapping("/addresses")
    public List<AddressResponse> listAddresses(@AuthenticationPrincipal AuthenticatedUser principal) {
        return customerProfileService.listAddresses(principal.userId());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @Valid @RequestBody AddressRequest request) {
        return customerProfileService.createAddress(principal.userId(), request);
    }

    @PutMapping("/addresses/{addressId}")
    public AddressResponse updateAddress(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable UUID addressId,
                                          @Valid @RequestBody AddressRequest request) {
        return customerProfileService.updateAddress(principal.userId(), addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID addressId) {
        customerProfileService.deleteAddress(principal.userId(), addressId);
    }

    @GetMapping("/pets")
    public List<PetResponse> listPets(@AuthenticationPrincipal AuthenticatedUser principal) {
        return customerProfileService.listPets(principal.userId());
    }

    @PostMapping("/pets")
    @ResponseStatus(HttpStatus.CREATED)
    public PetResponse createPet(@AuthenticationPrincipal AuthenticatedUser principal,
                                  @Valid @RequestBody PetRequest request) {
        return customerProfileService.createPet(principal.userId(), request);
    }

    @PutMapping("/pets/{petId}")
    public PetResponse updatePet(@AuthenticationPrincipal AuthenticatedUser principal,
                                  @PathVariable UUID petId,
                                  @Valid @RequestBody PetRequest request) {
        return customerProfileService.updatePet(principal.userId(), petId, request);
    }

    @DeleteMapping("/pets/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePet(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID petId) {
        customerProfileService.deletePet(principal.userId(), petId);
    }
}
