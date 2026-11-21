package com.vetclinic.profile.service;

import com.vetclinic.profile.exception.PhotoUploadException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudinaryUploaderTest {

    private static final String SECRET = "test-secret";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.test/v1_1");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CloudinaryUploader uploader = new CloudinaryUploader("demo", "key-1", SECRET, builder.build());

    @Test
    void isDisabledUnlessAllThreeSettingsAreGiven() {
        RestClient client = RestClient.create();
        assertThat(new CloudinaryUploader("", "k", "s", client).enabled()).isFalse();
        assertThat(new CloudinaryUploader("c", "", "s", client).enabled()).isFalse();
        assertThat(new CloudinaryUploader("c", "k", "  ", client).enabled()).isFalse();
        assertThat(new CloudinaryUploader(null, null, null, client).enabled()).isFalse();
        assertThat(uploader.enabled()).isTrue();
    }

    @Test
    void upload_sendsASignedRequest_andReturnsTheSecureUrl() {
        String publicId = "vetclinic/doctors/" + UUID.randomUUID();
        AtomicReference<String> body = new AtomicReference<>();
        server.expect(requestTo("https://api.test/v1_1/demo/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> body.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v7/x.jpg\"}",
                        MediaType.APPLICATION_JSON));

        String url = uploader.upload(publicId, new byte[]{1, 2, 3});

        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/v7/x.jpg");
        String sent = body.get();
        assertThat(field(sent, "api_key")).isEqualTo("key-1");
        assertThat(field(sent, "public_id")).isEqualTo(publicId);
        assertThat(field(sent, "overwrite")).isEqualTo("true");
        assertThat(field(sent, "invalidate")).isEqualTo("true");
        // Chữ ký = SHA-1(các tham số theo thứ tự tên + secret); secret không được gửi đi.
        String timestamp = field(sent, "timestamp");
        assertThat(field(sent, "signature")).isEqualTo(sha1(
                "invalidate=true&overwrite=true&public_id=" + publicId + "&timestamp=" + timestamp + SECRET));
        assertThat(sent).doesNotContain(SECRET);
        server.verify();
    }

    @Test
    void upload_failureFromCloudinary_becomesAPhotoUploadException() {
        server.expect(requestTo("https://api.test/v1_1/demo/image/upload"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid Signature\"}}"));

        assertThatThrownBy(() -> uploader.upload("vetclinic/staff/x", new byte[]{1}))
                .isInstanceOf(PhotoUploadException.class);
    }

    @Test
    void upload_responseWithoutUrl_isAFailure() {
        server.expect(requestTo("https://api.test/v1_1/demo/image/upload"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> uploader.upload("vetclinic/staff/x", new byte[]{1}))
                .isInstanceOf(PhotoUploadException.class);
    }

    private static String field(String multipartBody, String name) {
        Matcher m = Pattern.compile("name=\"" + name + "\"[^\\r]*\\r\\n(?:[A-Za-z-]+: [^\\r]*\\r\\n)*\\r\\n([^\\r]*)").matcher(multipartBody);
        assertThat(m.find()).as("trường %s trong request", name).isTrue();
        return m.group(1);
    }

    private static String sha1(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
