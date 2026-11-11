package com.vetclinic.booking.service;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.BlockedDoctor;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.repository.BlockedDoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Bác sĩ đang làm việc: có trong danh sách của profile-service và không bị khoá (CN-08). */
@Component
@RequiredArgsConstructor
public class DoctorDirectory {

    private final ProfileServiceClient profileServiceClient;
    private final BlockedDoctorRepository blockedDoctorRepository;

    /**
     * Không hỏi được profile-service thì báo 503 chứ không trả danh sách rỗng: rỗng nghĩa là
     * "không có bác sĩ", và các chỗ gọi sẽ từ chối đặt lịch hoặc xếp nhầm theo kết luận sai đó.
     */
    public List<DoctorSummaryResponse> activeDoctors() {
        Set<UUID> blocked = blockedDoctorRepository.findAll().stream()
                .map(BlockedDoctor::getDoctorUserId).collect(Collectors.toSet());
        try {
            return profileServiceClient.listDoctors().stream()
                    .filter(d -> d.userId() != null && !blocked.contains(d.userId()))
                    .collect(Collectors.toMap(DoctorSummaryResponse::userId, d -> d, (a, b) -> a))
                    .values().stream().toList();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa kiểm tra được lịch phòng khám, bạn thử lại sau ít phút.");
        }
    }

    public long activeDoctorCount() {
        return activeDoctors().stream().map(DoctorSummaryResponse::userId).filter(Objects::nonNull).count();
    }
}
