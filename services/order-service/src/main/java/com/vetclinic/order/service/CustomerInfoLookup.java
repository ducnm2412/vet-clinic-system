package com.vetclinic.order.service;

import com.vetclinic.order.client.AuthClient;
import com.vetclinic.order.client.ProfileClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tra họ tên (auth-service) và số điện thoại (profile-service) của một khách. Lỗi ở một bên chỉ làm
 * thiếu đúng phần đó: hoá đơn vẫn mở được, nhân viên gõ tay nếu cần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerInfoLookup {

    // Cùng quy tắc với CollectCounterInvoiceRequest: số lạ định dạng thì bỏ, đừng làm hỏng ô nhập của nhân viên.
    private static final Pattern PHONE = Pattern.compile("^0[0-9]{9}$");

    private final AuthClient authClient;
    private final ProfileClient profileClient;

    /** Tên hoặc số điện thoại có thể null khi tra không ra. */
    public record CustomerInfo(String name, String phone) {
        public boolean isEmpty() {
            return name == null && phone == null;
        }
    }

    public CustomerInfo find(UUID userId, String bearerToken) {
        return new CustomerInfo(lookupName(userId, bearerToken), lookupPhone(userId, bearerToken));
    }

    private String lookupName(UUID userId, String bearerToken) {
        try {
            AuthClient.CustomerAccountView account = authClient.getCustomer(userId, bearerToken);
            String name = ((account.firstName() == null ? "" : account.firstName()) + " "
                    + (account.lastName() == null ? "" : account.lastName())).strip();
            return name.isEmpty() ? null : name;
        } catch (FeignException e) {
            log.warn("Không lấy được tên khách {} (HTTP {})", userId, e.status());
            return null;
        }
    }

    private String lookupPhone(UUID userId, String bearerToken) {
        try {
            String phone = profileClient.getCustomerProfile(userId, bearerToken).phone();
            return phone != null && PHONE.matcher(phone.strip()).matches() ? phone.strip() : null;
        } catch (FeignException e) {
            log.warn("Không lấy được số điện thoại khách {} (HTTP {})", userId, e.status());
            return null;
        }
    }
}
