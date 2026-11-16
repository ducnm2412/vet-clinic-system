package com.vetclinic.notification.service;

import com.vetclinic.notification.domain.Notification;
import com.vetclinic.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceCreateOnceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repository);

    @Test
    void createsTheFirstTime_andSkipsWhenThePersonAlreadyHasOne() {
        UUID user = UUID.randomUUID();
        when(repository.existsByUserIdAndType(user, "PROFILE_SETUP")).thenReturn(false, true);

        assertThat(service.createOnce(user, "PROFILE_SETUP", "t", "m", "/x")).isTrue();
        assertThat(service.createOnce(user, "PROFILE_SETUP", "t", "m", "/x")).isFalse();

        verify(repository, org.mockito.Mockito.times(1)).save(any(Notification.class));
    }

    @Test
    void anotherUserIsNotAffected() {
        UUID user = UUID.randomUUID();
        when(repository.existsByUserIdAndType(user, "PROFILE_SETUP")).thenReturn(false);

        service.createOnce(user, "PROFILE_SETUP", "t", "m", null);

        verify(repository).save(any(Notification.class));
        verify(repository, never()).deleteOlderThan(any());
    }
}
