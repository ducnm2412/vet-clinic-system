package com.vetclinic.profile.service;

import com.vetclinic.profile.domain.Address;
import com.vetclinic.profile.domain.CustomerProfile;
import com.vetclinic.profile.domain.Pet;
import com.vetclinic.profile.dto.CustomerSummaryResponse;
import com.vetclinic.profile.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tra hồ sơ nhiều khách một lượt theo userId — đúng các dòng đang hiện trên một trang của bảng
 * Khách hàng, nên không cần endpoint liệt kê toàn bộ. Khách chưa từng mở trang hồ sơ thì không có
 * dòng trả về.
 */
@Service
@RequiredArgsConstructor
public class CustomerSummaryService {

    static final int MAX_IDS = 100;

    private final CustomerProfileRepository customerProfileRepository;

    @Transactional(readOnly = true)
    public List<CustomerSummaryResponse> summarize(List<UUID> userIds) {
        Set<UUID> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tối đa " + MAX_IDS + " khách một lần");
        }
        return customerProfileRepository.findByUserIdIn(ids).stream().map(CustomerSummaryService::toSummary).toList();
    }

    private static CustomerSummaryResponse toSummary(CustomerProfile profile) {
        String address = profile.getAddresses().stream()
                .sorted(Comparator.comparing(Address::isDefault).reversed()
                        .thenComparing(Address::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .map(CustomerSummaryService::format)
                .orElse(null);
        List<String> pets = profile.getPets().stream()
                .sorted(Comparator.comparing(Pet::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Pet::getName)
                .toList();
        return new CustomerSummaryResponse(profile.getUserId(), profile.getPhone(), address, pets);
    }

    private static String format(Address a) {
        return Stream.of(a.getLine1(), a.getLine2(), a.getWard(), a.getCity())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }
}
