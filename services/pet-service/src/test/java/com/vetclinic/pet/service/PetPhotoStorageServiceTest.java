package com.vetclinic.pet.service;

import com.vetclinic.pet.exception.InvalidPhotoException;
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

class PetPhotoStorageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @TempDir
    Path dir;

    private final CloudinaryUploader cloudinary = mock(CloudinaryUploader.class);
    private final UUID petId = UUID.randomUUID();

    private PetPhotoStorageService service() {
        return new PetPhotoStorageService(dir.toString(), cloudinary);
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "a.png", "image/png", PNG);
    }

    @Test
    void withCloudinary_theImageGoesThereAndTheUrlIsReturned_nothingIsWrittenToDisk() {
        when(cloudinary.enabled()).thenReturn(true);
        when(cloudinary.upload(eq("vetclinic/pets/" + petId), any())).thenReturn("https://res.cloudinary.com/p.png");

        assertThat(service().store(petId, png())).isEqualTo("https://res.cloudinary.com/p.png");
        assertThat(dir.resolve("pets")).doesNotExist();
    }

    @Test
    void withoutCloudinary_theImageStaysOnDisk_andNoUrlIsReturned() {
        when(cloudinary.enabled()).thenReturn(false);

        assertThat(service().store(petId, png())).isNull();
        assertThat(dir.resolve("pets").resolve(petId + ".png")).isRegularFile();
        verify(cloudinary, never()).upload(anyString(), any());
    }

    @Test
    void notAnImage_isRefusedBeforeAnythingIsUploaded() {
        when(cloudinary.enabled()).thenReturn(true);
        var notImage = new MockMultipartFile("file", "a.png", "image/png", "<html>".getBytes());

        assertThatThrownBy(() -> service().store(petId, notImage)).isInstanceOf(InvalidPhotoException.class);
        verify(cloudinary, never()).upload(anyString(), any());
    }

    @Test
    void legacyFile_isMovedToCloudinary_whenItExists_andLeftAloneWhenCloudinaryIsOff() throws Exception {
        Files.createDirectories(dir.resolve("pets"));
        Files.write(dir.resolve("pets").resolve(petId + ".png"), PNG);
        when(cloudinary.upload(eq("vetclinic/pets/" + petId), any())).thenReturn("https://res.cloudinary.com/old.png");

        when(cloudinary.enabled()).thenReturn(false);
        assertThat(service().uploadLegacyFile(petId)).isNull();

        when(cloudinary.enabled()).thenReturn(true);
        assertThat(service().uploadLegacyFile(petId)).isEqualTo("https://res.cloudinary.com/old.png");
        assertThat(service().uploadLegacyFile(UUID.randomUUID())).isNull();
    }

    @Test
    void deletingAPet_alsoDeletesItsCloudinaryImage() {
        service().delete(petId);

        verify(cloudinary).delete("vetclinic/pets/" + petId);
    }
}
