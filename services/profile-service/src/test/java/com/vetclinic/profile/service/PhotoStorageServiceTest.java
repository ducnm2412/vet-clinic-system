package com.vetclinic.profile.service;

import com.vetclinic.profile.dto.PhotoUrls;
import com.vetclinic.profile.exception.InvalidPhotoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoStorageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @TempDir
    Path dir;

    private final CloudinaryUploader cloudinary = mock(CloudinaryUploader.class);
    private final UUID id = UUID.randomUUID();

    private PhotoStorageService service() {
        return new PhotoStorageService(dir.toString(), cloudinary);
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "a.png", "image/png", PNG);
    }

    @Test
    void withCloudinary_theImageGoesThereAndTheUrlIsReturned_nothingIsWrittenToDisk() {
        when(cloudinary.enabled()).thenReturn(true);
        when(cloudinary.upload(eq("vetclinic/doctors/" + id), any())).thenReturn("https://res.cloudinary.com/x.png");

        String url = service().store(PhotoUrls.DOCTORS, id, png());

        assertThat(url).isEqualTo("https://res.cloudinary.com/x.png");
        assertThat(dir.resolve("doctors")).doesNotExist();
    }

    @Test
    void withoutCloudinary_theImageStaysOnDisk_andNoUrlIsReturned() {
        when(cloudinary.enabled()).thenReturn(false);

        String url = service().store(PhotoUrls.STAFF, id, png());

        assertThat(url).isNull();
        assertThat(dir.resolve("staff").resolve(id + ".png")).isRegularFile();
        verify(cloudinary, never()).upload(anyString(), any());
    }

    @Test
    void notAnImage_isRefusedBeforeAnythingIsUploaded() {
        when(cloudinary.enabled()).thenReturn(true);
        var notImage = new MockMultipartFile("file", "a.png", "image/png", "<html>".getBytes());

        assertThatThrownBy(() -> service().store(PhotoUrls.CUSTOMERS, id, notImage))
                .isInstanceOf(InvalidPhotoException.class);
        verify(cloudinary, never()).upload(anyString(), any());
    }

    @Test
    void legacyFile_isMovedToCloudinary_whenItExists() throws Exception {
        Files.createDirectories(dir.resolve("doctors"));
        Files.write(dir.resolve("doctors").resolve(id + ".png"), PNG);
        when(cloudinary.enabled()).thenReturn(true);
        when(cloudinary.upload(eq("vetclinic/doctors/" + id), any())).thenReturn("https://res.cloudinary.com/old.png");

        assertThat(service().uploadLegacyFile(PhotoUrls.DOCTORS, id)).isEqualTo("https://res.cloudinary.com/old.png");
        assertThat(service().uploadLegacyFile(PhotoUrls.DOCTORS, UUID.randomUUID())).isNull();
    }

    @Test
    void legacyFile_isLeftAloneWhenCloudinaryIsOff() throws Exception {
        Files.createDirectories(dir.resolve("doctors"));
        Files.write(dir.resolve("doctors").resolve(id + ".png"), PNG);
        when(cloudinary.enabled()).thenReturn(false);

        assertThat(service().uploadLegacyFile(PhotoUrls.DOCTORS, id)).isNull();
        verify(cloudinary, never()).upload(anyString(), any());
    }

    @Test
    void photoUrl_prefersTheStoredCloudinaryUrl_overTheLegacyDiskPath() {
        assertThat(PhotoUrls.resolve("https://res.cloudinary.com/x.png", PhotoUrls.DOCTORS, id, 3))
                .isEqualTo("https://res.cloudinary.com/x.png");
        assertThat(PhotoUrls.resolve(null, PhotoUrls.DOCTORS, id, 3)).isEqualTo("/profile/photos/doctors/" + id + "?v=3");
        assertThat(PhotoUrls.resolve("", PhotoUrls.DOCTORS, id, 0)).isNull();
    }
}
