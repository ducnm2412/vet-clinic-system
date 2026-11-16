package com.vetclinic.notification.service;

import com.vetclinic.notification.client.AuthServiceClient;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryTest {

    private final AuthServiceClient client = mock(AuthServiceClient.class);
    private final UserDirectory directory = new UserDirectory(client, "k");

    @Test
    void asksAuthServiceWithTheInternalKey_andRemembersTheAnswer() {
        List<UUID> staff = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(client.userIdsByRole("STAFF", "k")).thenReturn(staff);

        assertThat(directory.idsByRole("STAFF")).isEqualTo(staff);
        assertThat(directory.idsByRole("STAFF")).isEqualTo(staff);

        verify(client, times(1)).userIdsByRole("STAFF", "k");
    }

    @Test
    void differentRolesAreKeptApart() {
        UUID admin = UUID.randomUUID();
        when(client.userIdsByRole("ADMIN", "k")).thenReturn(List.of(admin));
        when(client.userIdsByRole("STAFF", "k")).thenReturn(List.of());

        assertThat(directory.idsByRole("ADMIN")).containsExactly(admin);
        assertThat(directory.idsByRole("STAFF")).isEmpty();
    }

    @Test
    void authServiceDownWithNothingRemembered_failsSoRabbitRetries() {
        when(client.userIdsByRole("STAFF", "k")).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> directory.idsByRole("STAFF")).isInstanceOf(FeignException.class);
    }
}
