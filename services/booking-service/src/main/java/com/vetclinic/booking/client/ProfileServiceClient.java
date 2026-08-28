package com.vetclinic.booking.client;

import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.dto.PetResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

// name = "profile-service" khớp spring.application.name bên profile-service — Feign tự resolve
// qua Eureka + LoadBalancer, không cần URL cứng hay @LoadBalancerClient thủ công.
@FeignClient(name = "profile-service")
public interface ProfileServiceClient {

    @GetMapping("/profile/customer/me/pets/{petId}")
    PetResponse getMyPet(@PathVariable("petId") UUID petId, @RequestHeader("Authorization") String bearerToken);

    @GetMapping("/profile/pets/{petId}")
    PetResponse getPetById(@PathVariable("petId") UUID petId, @RequestHeader("Authorization") String bearerToken);

    // Public — không cần Authorization header. Dùng để auto-generate slot cho toàn bộ bác sĩ
    // (SlotGenerationScheduler), không phải để hiển thị trang public listing của booking-service.
    @GetMapping("/profile/doctors")
    List<DoctorSummaryResponse> listDoctors();
}
