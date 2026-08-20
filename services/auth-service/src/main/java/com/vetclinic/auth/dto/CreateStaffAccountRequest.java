package com.vetclinic.auth.dto;

import com.vetclinic.auth.domain.RoleName;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStaffAccountRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull RoleName role
) {

    @AssertTrue(message = "role must be DOCTOR or STAFF")
    public boolean isRoleAllowed() {
        return role == RoleName.DOCTOR || role == RoleName.STAFF;
    }
}
