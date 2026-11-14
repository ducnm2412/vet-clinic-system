package com.vetclinic.booking.messaging;

import com.vetclinic.booking.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Bác sĩ đã lập bệnh án cho lịch hẹn → lịch hẹn đó đã khám xong, không còn nằm ở "chờ khám". */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionCreatedListener {

    private final AppointmentService appointmentService;

    @RabbitListener(queues = RabbitMQConfig.PRESCRIPTION_CREATED_QUEUE)
    public void onPrescriptionCreated(PrescriptionCreatedEvent event) {
        if (event == null || event.appointmentId() == null) {
            // Không ném: RabbitMQ sẽ giao lại mãi một message hỏng.
            log.warn("Bỏ qua prescription.created thiếu appointmentId");
            return;
        }
        appointmentService.completeAfterExam(event.appointmentId());
    }
}
