package com.vetclinic.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) String newPassword,
        @NotBlank String confirmPassword
) {

    @AssertTrue(message = "newPassword and confirmPassword must match")
    public boolean isConfirmPasswordMatching() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
