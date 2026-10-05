package com.vetclinic.pet.controller;

import com.vetclinic.pet.dto.MedicalRecordRequest;
import com.vetclinic.pet.dto.MedicalRecordResponse;
import com.vetclinic.pet.security.RoleUtils;
import com.vetclinic.pet.security.jwt.AuthenticatedUser;
import com.vetclinic.pet.service.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CN-23 → CN-25. Bệnh án khoá theo lịch hẹn nên đường dẫn là {@code /medical-records/by-appointment/{id}};
 * hai nhánh không gắn với một lịch hẹn cụ thể ({@code /pending}, {@code /mine/outstanding}) đặt
 * riêng, khai trước mẫu {@code by-appointment} không cần thiết vì tiền tố đã khác nhau hẳn.
 *
 * Lịch sử khám của một con vật nằm ở {@link PetController} vì nó là dữ liệu của con vật, không phải
 * của một lượt khám.
 */
@RestController
@RequestMapping("/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;

    /** PUT cho cả tạo và sửa: mỗi lịch hẹn chỉ có một bệnh án. */
    @PutMapping("/by-appointment/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public MedicalRecordResponse createOrUpdate(@PathVariable UUID appointmentId,
                                               @Valid @RequestBody MedicalRecordRequest request,
                                               @RequestHeader("Authorization") String bearerToken) {
        // Token của bác sĩ được chuyển tiếp sang booking-service để hỏi lịch hẹn — không tự ký
        // token nội bộ, service kia kiểm quyền như với người thật (VD-24).
        return medicalRecordService.createOrUpdate(appointmentId, request, bearerToken);
    }

    @GetMapping("/by-appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','DOCTOR','STAFF','ADMIN')")
    public MedicalRecordResponse getByAppointment(@PathVariable UUID appointmentId,
                                                 @AuthenticationPrincipal AuthenticatedUser me,
                                                 Authentication authentication) {
        boolean privileged = RoleUtils.hasAnyRole(authentication, "DOCTOR", "STAFF", "ADMIN");
        return medicalRecordService.getByAppointment(appointmentId, me.userId(), privileged);
    }

    /** Quầy giao thuốc sau khi khách đã trả tiền. Còn PENDING thì trả 409. */
    @PutMapping("/by-appointment/{appointmentId}/receive")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public MedicalRecordResponse receive(@PathVariable UUID appointmentId) {
        return medicalRecordService.receive(appointmentId);
    }

    /** Hàng đợi của quầy: mọi đơn đã thanh toán, chờ giao — gộp chung mọi bác sĩ. */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<MedicalRecordResponse> paidQueue() {
        return medicalRecordService.listPaidQueue();
    }

    /**
     * "Bệnh án còn treo" của chính bác sĩ đang đăng nhập — khác hẳn {@code /pending} ở trên, vốn là
     * hàng đợi đã thanh toán dùng chung của quầy.
     */
    @GetMapping("/mine/outstanding")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<MedicalRecordResponse> myOutstanding(@AuthenticationPrincipal AuthenticatedUser me) {
        return medicalRecordService.listOutstandingOf(me.userId());
    }
}
