package com.vetclinic.order.service;

import com.vetclinic.order.client.AuthClient;
import com.vetclinic.order.client.ProfileClient;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerInfoLookupTest {

    private static final String TOKEN = "Bearer staff";

    private final AuthClient authClient = mock(AuthClient.class);
    private final ProfileClient profileClient = mock(ProfileClient.class);
    private final CustomerInfoLookup lookup = new CustomerInfoLookup(authClient, profileClient);
    private final UUID userId = UUID.randomUUID();

    private static FeignException down() {
        return new FeignException.ServiceUnavailable("down",
                Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null),
                null, null);
    }

    @Test
    void joinsFirstAndLastNameAndReadsThePhone() {
        when(authClient.getCustomer(userId, TOKEN))
                .thenReturn(new AuthClient.CustomerAccountView(userId, "Nguyễn Thị", "Lan"));
        when(profileClient.getCustomerProfile(userId, TOKEN))
                .thenReturn(new ProfileClient.CustomerProfileView(userId, "0901234567"));

        var info = lookup.find(userId, TOKEN);

        assertThat(info.name()).isEqualTo("Nguyễn Thị Lan");
        assertThat(info.phone()).isEqualTo("0901234567");
    }

    @Test
    void oneServiceDownOnlyLosesItsOwnPart() {
        when(authClient.getCustomer(any(), any())).thenThrow(down());
        when(profileClient.getCustomerProfile(any(), any()))
                .thenReturn(new ProfileClient.CustomerProfileView(userId, "0901234567"));
        assertThat(lookup.find(userId, TOKEN).name()).isNull();
        assertThat(lookup.find(userId, TOKEN).phone()).isEqualTo("0901234567");

        // doReturn/doThrow: gọi lại when(mock.x()) sẽ chạy chính stub cũ và ném lỗi ngay trong test.
        doReturn(new AuthClient.CustomerAccountView(userId, "A", "B")).when(authClient).getCustomer(any(), any());
        doThrow(down()).when(profileClient).getCustomerProfile(any(), any());
        assertThat(lookup.find(userId, TOKEN).name()).isEqualTo("A B");
        assertThat(lookup.find(userId, TOKEN).phone()).isNull();
    }

    @Test
    void phoneInAnUnexpectedFormatIsIgnoredSoStaffIsNotBlockedByIt() {
        when(authClient.getCustomer(any(), any())).thenThrow(down());
        when(profileClient.getCustomerProfile(any(), any()))
                .thenReturn(new ProfileClient.CustomerProfileView(userId, "+84 901 234 567"));

        var info = lookup.find(userId, TOKEN);

        assertThat(info.phone()).isNull();
        assertThat(info.isEmpty()).isTrue();
    }
}
