package com.vetclinic.auth.security.facebook;

/** Ho so toi thieu doc tu Graph API /me sau khi accessToken da duoc xac minh la hop le. */
public record FacebookProfile(String id, String email, String firstName, String lastName) {
}
