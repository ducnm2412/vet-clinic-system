package com.vetclinic.booking.messaging;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.service.AppointmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// KHÔNG @Transactional ở class/method này — @TransactionalEventListener(AFTER_COMMIT) chỉ bắn
// event khi transaction tạo appointment THẬT SỰ commit, nếu test tự bọc rollback thì event
// không bao giờ được publish và test sẽ luôn timeout chờ message.
@SpringBootTest(properties = "eureka.client.enabled=false")
class AppointmentEventPublisherTest {

    private static final String TEST_QUEUE = "test.appointment-created";

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentSlotRepository appointmentSlotRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @MockBean
    private ProfileServiceClient profileServiceClient;

    @AfterEach
    void cleanup() {
        amqpAdmin.deleteQueue(TEST_QUEUE);
        appointmentRepository.deleteAll();
        appointmentSlotRepository.deleteAll();
    }

    @Test
    void createAppointment_afterCommit_publishesAppointmentCreatedEventToRabbitMQ() {
        Queue queue = new Queue(TEST_QUEUE, false, false, true);
        TopicExchange exchange = new TopicExchange(RabbitMQConfig.BOOKING_EVENTS_EXCHANGE, true, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with("appointment.created");
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);

        when(profileServiceClient.getMyPet(any(), any())).thenReturn(dummyPet());

        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 12, 10);
        LocalTime time = LocalTime.of(9, 0);
        AppointmentSlot slot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(doctorId).date(date).startTime(time).endTime(time.plusMinutes(30)).build());

        UUID customerUserId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        appointmentService.createAppointment(customerUserId, "Bearer test-token",
                new AppointmentRequest(petId, date, time, "Checkup"));

        AppointmentCreatedEvent event = (AppointmentCreatedEvent) rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);

        assertThat(event).isNotNull();
        assertThat(event.slotId()).isEqualTo(slot.getId());
        assertThat(event.doctorUserId()).isEqualTo(doctorId);
        assertThat(event.customerUserId()).isEqualTo(customerUserId);
        assertThat(event.petId()).isEqualTo(petId);
        assertThat(event.date()).isEqualTo(date);
        assertThat(event.startTime()).isEqualTo(time);
        assertThat(event.endTime()).isEqualTo(time.plusMinutes(30));
    }

    private PetResponse dummyPet() {
        return new PetResponse(UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE",
                LocalDate.of(2020, 1, 1), null, Instant.now(), Instant.now());
    }
}
