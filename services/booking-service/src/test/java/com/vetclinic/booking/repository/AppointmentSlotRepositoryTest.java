package com.vetclinic.booking.repository;

import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class AppointmentSlotRepositoryTest {

    @Autowired
    private AppointmentSlotRepository appointmentSlotRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    void findAvailableForUpdate_returnsMatchingAvailableSlot() {
        AppointmentSlot slot = appointmentSlotRepository.save(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID())
                .date(LocalDate.of(2026, 9, 1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build());

        List<AppointmentSlot> found = appointmentSlotRepository.findAvailableForUpdate(
                LocalDate.of(2026, 9, 1), LocalTime.of(10, 0));

        assertThat(found).extracting(AppointmentSlot::getId).contains(slot.getId());

        appointmentSlotRepository.deleteById(slot.getId());
    }

    @Test
    @Transactional
    void findAvailableForUpdate_ignoresAlreadyBookedSlot() {
        AppointmentSlot slot = appointmentSlotRepository.save(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID())
                .date(LocalDate.of(2026, 9, 1))
                .startTime(LocalTime.of(11, 0))
                .endTime(LocalTime.of(11, 30))
                .status(SlotStatus.BOOKED)
                .build());

        List<AppointmentSlot> found = appointmentSlotRepository.findAvailableForUpdate(
                LocalDate.of(2026, 9, 1), LocalTime.of(11, 0));

        assertThat(found).isEmpty();

        appointmentSlotRepository.deleteById(slot.getId());
    }

    // Chứng minh PESSIMISTIC_WRITE thật sự chống double-booking: 5 thread cùng cố book
    // 1 slot duy nhất tại cùng 1 thời điểm — chỉ đúng 1 thread được thành công.
    @Test
    void findAvailableForUpdate_preventsDoubleBooking_underConcurrency() throws InterruptedException {
        AppointmentSlot slot = appointmentSlotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID())
                .date(LocalDate.of(2026, 9, 2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(14, 30))
                .build());

        int threadCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                Boolean booked = txTemplate.execute(status -> {
                    List<AppointmentSlot> available = appointmentSlotRepository.findAvailableForUpdate(
                            LocalDate.of(2026, 9, 2), LocalTime.of(14, 0));
                    if (available.isEmpty()) {
                        return false;
                    }
                    AppointmentSlot s = available.get(0);
                    s.setStatus(SlotStatus.BOOKED);
                    appointmentSlotRepository.saveAndFlush(s);
                    return true;
                });

                if (Boolean.TRUE.equals(booked)) {
                    successCount.incrementAndGet();
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);

        appointmentSlotRepository.deleteById(slot.getId());
    }
}
