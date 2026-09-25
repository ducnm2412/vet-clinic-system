package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.ClinicService;
import com.vetclinic.booking.dto.ClinicServiceRequest;
import com.vetclinic.booking.dto.ClinicServiceResponse;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.repository.ClinicServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * VD-21: danh mục dịch vụ của phòng khám.
 *
 * Không có xoá, chỉ ẩn — lịch hẹn cũ vẫn trỏ tới dịch vụ đó, giống cách đã làm với sản phẩm
 * (VD-03) và tài khoản (CN-08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicServiceCatalog {

    private final ClinicServiceRepository clinicServiceRepository;

    /** Trang chủ và trang đặt lịch chỉ thấy dịch vụ đang cung cấp; quản trị thấy cả dịch vụ đã ẩn. */
    @Transactional(readOnly = true)
    public List<ClinicServiceResponse> list(boolean includeInactive) {
        List<ClinicService> services = includeInactive
                ? clinicServiceRepository.findAllByOrderByDisplayOrderAscNameAsc()
                : clinicServiceRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        return services.stream().map(ClinicServiceCatalog::toResponse).toList();
    }

    @Transactional
    public ClinicServiceResponse create(ClinicServiceRequest request) {
        if (clinicServiceRepository.existsBySlug(request.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã có dịch vụ dùng mã " + request.slug());
        }

        ClinicService service = ClinicService.builder()
                .slug(request.slug())
                .name(request.name().trim())
                .description(request.description())
                .durationMinutes(request.durationMinutes() == null ? 30 : request.durationMinutes())
                .referencePrice(request.referencePrice())
                .displayOrder(request.displayOrder() == null ? 0 : request.displayOrder())
                .build();
        log.info("Thêm dịch vụ {}", service.getSlug());
        return toResponse(clinicServiceRepository.saveAndFlush(service));
    }

    @Transactional
    public ClinicServiceResponse update(UUID id, ClinicServiceRequest request) {
        ClinicService service = findOrThrow(id);
        if (clinicServiceRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã có dịch vụ dùng mã " + request.slug());
        }

        service.setSlug(request.slug());
        service.setName(request.name().trim());
        service.setDescription(request.description());
        if (request.durationMinutes() != null) {
            service.setDurationMinutes(request.durationMinutes());
        }
        service.setReferencePrice(request.referencePrice());
        if (request.displayOrder() != null) {
            service.setDisplayOrder(request.displayOrder());
        }
        return toResponse(clinicServiceRepository.saveAndFlush(service));
    }

    @Transactional
    public ClinicServiceResponse setActive(UUID id, boolean active) {
        ClinicService service = findOrThrow(id);
        service.setActive(active);
        log.info("{} dịch vụ {}", active ? "Cung cấp lại" : "Ngừng cung cấp", service.getSlug());
        return toResponse(clinicServiceRepository.saveAndFlush(service));
    }

    /**
     * Dịch vụ khách chọn lúc đặt lịch. Null nghĩa là khách không chọn — vẫn đặt được, phòng khám
     * đọc ô lý do khám.
     */
    @Transactional(readOnly = true)
    public ClinicService requireBookable(UUID serviceId) {
        if (serviceId == null) {
            return null;
        }
        ClinicService service = findOrThrow(serviceId);
        if (!Boolean.TRUE.equals(service.getActive())) {
            // Dịch vụ vừa bị ngừng giữa lúc khách đang mở trang đặt lịch: nói rõ thay vì lặng lẽ bỏ.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng khám đã ngừng dịch vụ " + service.getName());
        }
        return service;
    }

    private ClinicService findOrThrow(UUID id) {
        return clinicServiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ: " + id));
    }

    private static ClinicServiceResponse toResponse(ClinicService s) {
        return new ClinicServiceResponse(s.getId(), s.getSlug(), s.getName(), s.getDescription(),
                s.getDurationMinutes(), s.getReferencePrice(), Boolean.TRUE.equals(s.getActive()),
                s.getDisplayOrder());
    }
}
