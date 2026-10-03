package com.vetclinic.auth.security.facebook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vetclinic.auth.exception.FacebookEmailRequiredException;
import com.vetclinic.auth.exception.InvalidFacebookTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Xac minh accessToken cua Facebook. Khac voi Google (JWT tu ky, kiem cuc bo), Facebook chi
 * cap access token thuong nen phai goi nguoc len Graph API:
 *
 *  1. /debug_token - dung app access token (appId|appSecret) de hoi token nay co hop le va co
 *     dung la cap cho app cua minh khong (chan truong hop ai do gui token hop le cua MOT app
 *     Facebook khac).
 *  2. /me - lay ho so (id, email, ho ten) bang chinh access token cua nguoi dung.
 *
 * appSecret la bi mat that (khong nhu Google client-id), chi dung o day, khong bao gio dua
 * sang frontend.
 */
@Component
@RequiredArgsConstructor
public class FacebookTokenVerifier {

    private static final String GRAPH_BASE = "https://graph.facebook.com";

    private final FacebookProperties facebookProperties;
    private final RestClient restClient = RestClient.create();

    public FacebookProfile verify(String userAccessToken) {
        DebugTokenResponse debug = debugToken(userAccessToken);

        if (debug == null || debug.data() == null
                || !debug.data().isValid()
                || facebookProperties.getAppId() == null
                || !facebookProperties.getAppId().equals(debug.data().appId())) {
            throw new InvalidFacebookTokenException();
        }

        GraphMeResponse me = fetchProfile(userAccessToken);
        if (me == null || me.id() == null) {
            throw new InvalidFacebookTokenException();
        }
        if (me.email() == null || me.email().isBlank()) {
            throw new FacebookEmailRequiredException();
        }

        return new FacebookProfile(me.id(), me.email(), me.firstName(), me.lastName());
    }

    private DebugTokenResponse debugToken(String userAccessToken) {
        try {
            return restClient.get()
                    .uri("{base}/{version}/debug_token?input_token={input}&access_token={appId}|{appSecret}",
                            GRAPH_BASE, facebookProperties.getApiVersion(), userAccessToken,
                            facebookProperties.getAppId(), facebookProperties.getAppSecret())
                    .retrieve()
                    .body(DebugTokenResponse.class);
        } catch (RestClientException e) {
            throw new InvalidFacebookTokenException();
        }
    }

    private GraphMeResponse fetchProfile(String userAccessToken) {
        try {
            return restClient.get()
                    .uri("{base}/{version}/me?fields=id,email,first_name,last_name&access_token={token}",
                            GRAPH_BASE, facebookProperties.getApiVersion(), userAccessToken)
                    .retrieve()
                    .body(GraphMeResponse.class);
        } catch (RestClientException e) {
            throw new InvalidFacebookTokenException();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DebugTokenResponse(DebugTokenData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DebugTokenData(
            @JsonProperty("is_valid") boolean isValid,
            @JsonProperty("app_id") String appId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphMeResponse(
            String id,
            String email,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName
    ) {
    }
}
