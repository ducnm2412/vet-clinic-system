package com.vetclinic.auth.security.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.vetclinic.auth.exception.InvalidGoogleTokenException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Xac minh ID token cua Google ngay tren server (chu ky, issuer, audience, han dung) - khong
 * goi ngược Google moi lan dang nhap: GoogleIdTokenVerifier tu tai va cache bo khoa cong khai
 * cua Google, chi lam moi khi bo khoa xoay vong.
 */
@Component
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private final GoogleProperties googleProperties;
    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleProperties.getClientId()))
                .build();
    }

    public GoogleIdToken.Payload verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new InvalidGoogleTokenException();
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException();
        }
    }
}
