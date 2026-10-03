package com.vetclinic.auth.dto;

/** Thân JSON của /login, /google, /facebook, /refresh — refresh token không còn ở đây, nó đi qua
 * Set-Cookie httpOnly (xem RefreshCookieFactory) để JavaScript không đọc được. */
public record AccessTokenResponse(String accessToken) {
}
