package com.vetclinic.booking.client;

import com.vetclinic.booking.dto.DoctorSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

// name = "profile-service" khớp spring.application.name bên profile-service — Feign tự resolve
// qua Eureka + LoadBalancer, không cần URL cứng hay @LoadBalancerClient thủ công.
@FeignClient(name = "profile-service")
public interface ProfileServiceClient {


    // Public — không cần Authorization header. Dùng để auto-generate slot cho toàn bộ bác sĩ
    // (SlotGenerationScheduler), không phải để hiển thị trang public listing của booking-service.
    @GetMapping("/profile/doctors")
    List<DoctorSummaryResponse> listDoctors();
}
