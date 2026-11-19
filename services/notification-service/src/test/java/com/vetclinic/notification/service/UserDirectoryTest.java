package com.vetclinic.notification.service;

import com.vetclinic.notification.client.AuthServiceClient;
import com.vetclinic.notification.client.StaffServiceClient;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
    private final StaffServiceClient staffClient = mock(StaffServiceClient.class);
    private final UserDirectory directory = new UserDirectory(client, staffClient, "k");

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

    @Test
    void staffOnShiftOrAll_keepsOnlyStaffWithAShiftThatDay() {
        UUID mai = UUID.randomUUID();
        UUID long_ = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 11, 20);
        when(client.userIdsByRole("STAFF", "k")).thenReturn(List.of(mai, long_));
        // Bác sĩ cũng có ca nhưng không phải nhân viên: bị loại.
        when(staffClient.userIdsOnShift("2026-11-20", "k")).thenReturn(List.of(mai, UUID.randomUUID()));

        assertThat(directory.staffOnShiftOrAll(day)).containsExactly(mai);
    }

    @Test
    void staffOnShiftOrAll_nobodyOnShift_fallsBackToEveryStaffMember() {
        List<UUID> staff = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(client.userIdsByRole("STAFF", "k")).thenReturn(staff);
        when(staffClient.userIdsOnShift("2026-11-20", "k")).thenReturn(List.of());

        assertThat(directory.staffOnShiftOrAll(LocalDate.of(2026, 11, 20))).isEqualTo(staff);
    }

    @Test
    void staffOnShiftOrAll_staffServiceDown_orNoDate_fallsBackToEveryStaffMember() {
        List<UUID> staff = List.of(UUID.randomUUID());
        when(client.userIdsByRole("STAFF", "k")).thenReturn(staff);
        when(staffClient.userIdsOnShift("2026-11-20", "k")).thenThrow(new RuntimeException("down"));

        assertThat(directory.staffOnShiftOrAll(LocalDate.of(2026, 11, 20))).isEqualTo(staff);
        assertThat(directory.staffOnShiftOrAll(null)).isEqualTo(staff);
        verify(staffClient, times(1)).userIdsOnShift("2026-11-20", "k");
    }
}
