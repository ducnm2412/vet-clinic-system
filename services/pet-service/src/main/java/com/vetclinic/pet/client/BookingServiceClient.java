package com.vetclinic.pet.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/**
 * Hỏi booking-service một lần duy nhất, lúc bác sĩ lập bệnh án: lịch hẹn này có thật không, của
 * con nào, của khách nào, bác sĩ nào khám.
 *
 * Không nhận ba thông tin đó từ client: nếu tin body gửi lên thì một bác sĩ có thể gắn bệnh án vào
 * con vật của người khác.
 *
 * Mọi lần ĐỌC bệnh án về sau không gọi lại service này — dữ liệu đã chép sẵn vào bệnh án. Vòng gọi
 * hai chiều (booking hỏi pet để lấy tên thú cưng, pet hỏi booking khi lập bệnh án) chỉ xảy ra lúc
 * chạy, Feign khởi tạo lười nên không service nào phải chờ service kia lúc khởi động.
 */
@FeignClient(name = "booking-service")
public interface BookingServiceClient {

    @GetMapping("/booking/appointments/{appointmentId}")
    AppointmentRef getAppointment(@PathVariable("appointmentId") UUID appointmentId,
                                 @RequestHeader("Authorization") String bearerToken);

    /** Chỉ lấy những trường bệnh án cần; các trường khác của booking-service Jackson bỏ qua. */
    record AppointmentRef(UUID id, UUID petId, UUID customerUserId, UUID doctorUserId, String status) {
    }
}
