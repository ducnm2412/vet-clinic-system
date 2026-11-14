package com.vetclinic.pet.messaging;

import com.vetclinic.pet.domain.MedicalRecord;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Bác sĩ vừa kê đơn lần đầu cho lịch hẹn này — payment-service nghe để tạo phiếu thu.
 *
 * Giữ nguyên hình dạng của bản cũ do booking-service phát (cùng tên trường, cùng routing key
 * {@code prescription.created}), chỉ đổi exchange sang {@code pet.events}.
 *
 * {@code unitPrice} là giá trong danh mục thuốc tại lúc kê (chốt vào phiếu thu, đổi giá danh mục
 * sau này không làm đổi phiếu). Null khi thuốc không có trong danh mục hoặc chưa có giá — quầy
 * nhập tay. Số lượng không có ở đây: quầy nhập theo số thực tế phát ra.
 */
public record PrescriptionCreatedEvent(
        UUID medicalRecordId,
        UUID appointmentId,
        UUID customerUserId,
        List<Item> items,
        // Dịch vụ khách đã đặt và giá tham khảo của nó, để phiếu thu tính tiền dịch vụ cạnh tiền thuốc.
        // Null khi lịch không chọn dịch vụ hoặc dịch vụ chưa có giá.
        String serviceName,
        BigDecimal servicePrice
) {
    public record Item(String medicationName, String dosage, String frequency, Integer durationDays,
                       BigDecimal unitPrice) {
    }

    /** @param priceOf tra giá theo tên thuốc người kê gõ; trả null nếu không có giá. */
    public static PrescriptionCreatedEvent of(MedicalRecord record, Function<String, BigDecimal> priceOf,
                                              String serviceName, BigDecimal servicePrice) {
        return new PrescriptionCreatedEvent(record.getId(), record.getAppointmentId(), record.getCustomerUserId(),
                record.getPrescriptionItems().stream()
                        .map(item -> new Item(item.getMedicationName(), item.getDosage(), item.getFrequency(),
                                item.getDurationDays(), priceOf.apply(item.getMedicationName())))
                        .toList(),
                serviceName, servicePrice);
    }
}
