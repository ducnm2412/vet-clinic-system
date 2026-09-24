package com.vetclinic.booking.controller;

import com.vetclinic.booking.dto.MedicalRecordRequest;
import com.vetclinic.booking.dto.MedicalRecordResponse;
import com.vetclinic.booking.security.RoleUtils;
import com.vetclinic.booking.security.jwt.AuthenticatedUser;
import com.vetclinic.booking.service.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    @PutMapping("/booking/appointments/{id}/medical-record")
    @PreAuthorize("hasRole('DOCTOR')")
    public MedicalRecordResponse createOrUpdateMedicalRecord(@PathVariable UUID id,
                                                               @Valid @RequestBody MedicalRecordRequest request) {
        return medicalRecordService.createOrUpdateMedicalRecord(id, request);
    }

    @GetMapping("/booking/appointments/{id}/medical-record")
    @PreAuthorize("hasAnyRole('CUSTOMER','DOCTOR','STAFF','ADMIN')")
    public MedicalRecordResponse getMedicalRecord(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   Authentication authentication,
                                                   @PathVariable UUID id) {
        boolean privileged = RoleUtils.hasAnyRole(authentication, "DOCTOR", "STAFF", "ADMIN");
        return medicalRecordService.getMedicalRecord(id, principal.userId(), privileged);
    }

    // Staff tiếp nhận đơn thuốc sau khi khách hàng đã thanh toán (PAID -> RECEIVED).
    // Trả lỗi 409 nếu đơn còn PENDING (chưa thanh toán).
    @PutMapping("/booking/appointments/{id}/medical-record/receive")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public MedicalRecordResponse receivePrescription(@PathVariable UUID id) {
        return medicalRecordService.receivePrescription(id);
    }

    // Hàng đợi cho staff: toàn bộ đơn thuốc đã PAID (đã thanh toán) chờ tiếp nhận, không gắn
    // theo 1 appointment cụ thể nên đặt ở path riêng /booking/medical-records/..., không lồng
    // dưới /booking/appointments/{id}/...
    @GetMapping("/booking/medical-records/pending")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<MedicalRecordResponse> listPendingPrescriptions() {
        return medicalRecordService.listPendingPrescriptions();
    }

    // "Bệnh án còn treo" của bác sĩ: đơn thuốc CHÍNH BÁC SĨ NÀY kê, chưa trả tiền hoặc đã trả
    // nhưng chưa phát — không dùng chung endpoint /pending ở trên vì đó là hàng đợi PAID dành
    // riêng cho staff (mọi bác sĩ gộp chung, không lọc theo người kê).
    @GetMapping("/booking/medical-records/mine/outstanding")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<MedicalRecordResponse> listMyOutstandingPrescriptions(@AuthenticationPrincipal AuthenticatedUser principal) {
        return medicalRecordService.listMyOutstandingPrescriptions(principal.userId());
    }

    // Lịch sử khám của 1 thú cưng — dùng khi bác sĩ/nhân viên bấm vào 1 thú cưng ở màn tra
    // cứu chung (GET /profile/pets) để xem lại các lần khám trước, không chỉ ca hiện tại.
    @GetMapping("/booking/medical-records/by-pet/{petId}")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public List<MedicalRecordResponse> listByPet(@PathVariable UUID petId) {
        return medicalRecordService.listByPet(petId);
    }
}
