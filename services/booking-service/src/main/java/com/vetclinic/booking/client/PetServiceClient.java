package com.vetclinic.booking.client;

import com.vetclinic.booking.dto.PetResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * Hồ sơ thú cưng nằm ở pet-service (tách khỏi profile-service ngày 24/09/2026 — VD-10).
 *
 * Token của người gọi được chuyển nguyên sang: khách đặt lịch thì pet-service tự kiểm con vật
 * đó có phải của họ không, nên booking-service không phải tự đoán quyền sở hữu.
 */
@FeignClient(name = "pet-service")
public interface PetServiceClient {

    /** Thú cưng của CHÍNH khách đang gọi; không phải của họ thì 404. */
    @GetMapping("/pets/me/{petId}")
    PetResponse getMyPet(@PathVariable("petId") UUID petId, @RequestHeader("Authorization") String bearerToken);

    /** Tra cứu cho bác sĩ / nhân viên. */
    @GetMapping("/pets/{petId}")
    PetResponse getPetById(@PathVariable("petId") UUID petId, @RequestHeader("Authorization") String bearerToken);
}
