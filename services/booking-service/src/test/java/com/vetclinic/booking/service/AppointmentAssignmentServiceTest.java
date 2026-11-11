package com.vetclinic.booking.service;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.domain.BlockedDoctor;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.domain.DoctorShift;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.DoctorAvailabilityResponse;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.BlockedDoctorRepository;
import com.vetclinic.booking.repository.DoctorShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Admin xếp bác sĩ cho yêu cầu đặt lịch, đề xuất giờ khác, và khách đồng ý. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class AppointmentAssignmentServiceTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @Autowired
    private AppointmentAssignmentService assignment;
    @Autowired
    private AppointmentRequestService requests;
    @Autowired
    private AppointmentService appointments;
    @Autowired
    private AppointmentSlotRepository slotRepository;
    @Autowired
    private DoctorShiftRepository shiftRepository;
    @Autowired
    private BlockedDoctorRepository blockedDoctorRepository;

    @MockBean
    private PetServiceClient petServiceClient;
    @MockBean
    private ProfileServiceClient profileServiceClient;

    private final LocalDate day = LocalDate.now(ClinicSchedule.ZONE_ID).plusDays(14);
    private UUID drA;
    private UUID drB;

    @BeforeEach
    void setUp() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(new PetResponse(UUID.randomUUID(),
                UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE", LocalDate.of(2020, 1, 1), null, null, null,
                Instant.now(), Instant.now()));
        drA = UUID.randomUUID();
        drB = UUID.randomUUID();
        when(profileServiceClient.listDoctors()).thenReturn(List.of(
                new DoctorSummaryResponse(UUID.randomUUID(), drA, "BS An", "Nội khoa", null, null),
                new DoctorSummaryResponse(UUID.randomUUID(), drB, "BS Bình", "Da liễu", null, null)));
    }

    // ---- xếp bác sĩ ----

    @Test
    void assign_confirmsAndGivesTheDoctorASlot_withoutAnyShift() {
        AppointmentResponse req = request(NINE);

        AppointmentResponse r = assignment.assign(req.id(), drA);

        assertThat(r.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(r.doctorUserId()).isEqualTo(drA);
        assertThat(r.slotId()).isNotNull();
        assertThat(r.date()).isEqualTo(day);
        assertThat(r.startTime()).isEqualTo(NINE);
        AppointmentSlot slot = slotRepository.findById(r.slotId()).orElseThrow();
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        // Từ đây là lịch thường: bác sĩ thấy nó trong lịch của mình.
        assertThat(appointments.search(day, null, drA)).extracting(AppointmentResponse::id).contains(req.id());
    }

    @Test
    void assign_reusesAnExistingFreeSlot_insteadOfDuplicatingIt() {
        AppointmentSlot existing = slotRepository.saveAndFlush(slot(drA, NINE, SlotStatus.AVAILABLE));

        AppointmentResponse r = assignment.assign(request(NINE).id(), drA);

        assertThat(r.slotId()).isEqualTo(existing.getId());
        assertThat(slotRepository.findByDoctorUserIdAndDate(drA, day)).hasSize(1);
    }

    @Test
    void assign_refusesADoctorWhoAlreadyHasAnAppointmentAtThatTime() {
        assignment.assign(request(NINE).id(), drA);
        AppointmentResponse second = request(NINE);

        assertConflict(() -> assignment.assign(second.id(), drA));
        // Bác sĩ còn lại thì được.
        assertThat(assignment.assign(second.id(), drB).status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void assign_refusesBlockedAndUnknownDoctors() {
        AppointmentResponse req = request(NINE);
        blockedDoctorRepository.saveAndFlush(new BlockedDoctor(drA, Instant.now()));

        assertConflict(() -> assignment.assign(req.id(), drA));
        assertConflict(() -> assignment.assign(req.id(), UUID.randomUUID()));
    }

    @Test
    void assign_onlyWorksOnRequestsWaitingForADoctor() {
        AppointmentResponse req = request(NINE);
        assignment.assign(req.id(), drA);
        // Đã xếp rồi: xếp lần nữa là sai.
        assertConflict(() -> assignment.assign(req.id(), drB));

        AppointmentResponse cancelled = request(TEN);
        appointments.cancelAppointment(cancelled.id(), cancelled.customerUserId(), false);
        assertConflict(() -> assignment.assign(cancelled.id(), drA));
    }

    @Test
    void assign_unknownAppointment_isNotFound() {
        assertThatThrownBy(() -> assignment.assign(UUID.randomUUID(), drA))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- đề xuất giờ khác ----

    @Test
    void propose_reservesTheDoctorsSlotAndWaitsForTheCustomer() {
        AppointmentResponse req = request(NINE);

        AppointmentResponse r = assignment.propose(req.id(), drB, day, TEN);

        assertThat(r.status()).isEqualTo(AppointmentStatus.AWAITING_CUSTOMER);
        assertThat(r.proposedDate()).isEqualTo(day);
        assertThat(r.proposedStartTime()).isEqualTo(TEN);
        // Giờ khám hiện là giờ đề xuất; giờ khách chọn ban đầu vẫn còn để so sánh.
        assertThat(r.startTime()).isEqualTo(TEN);
        assertThat(r.preferredStartTime()).isEqualTo(NINE);
        assertThat(r.doctorUserId()).isEqualTo(drB);
        assertThat(slotRepository.findById(r.slotId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void propose_doesNotAppearInTheDoctorsScheduleUntilTheCustomerAccepts() {
        AppointmentResponse req = request(NINE);
        assignment.propose(req.id(), drB, day, TEN);
        // search() thấy (admin cần thấy); lọc "chưa phải lịch thật" làm ở endpoint của bác sĩ.
        assertThat(appointments.search(day, AppointmentStatus.AWAITING_CUSTOMER, drB)).hasSize(1);
    }

    @Test
    void propose_sameTimeAsRequested_isRejected() {
        AppointmentResponse req = request(NINE);
        assertThatThrownBy(() -> assignment.propose(req.id(), drA, day, NINE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void propose_cannotUseABusyDoctor() {
        slotRepository.saveAndFlush(slot(drB, TEN, SlotStatus.BOOKED));
        AppointmentResponse req = request(NINE);
        assertConflict(() -> assignment.propose(req.id(), drB, day, TEN));
    }

    @Test
    void propose_respectsTheTimeCapacity() {
        // Hai bác sĩ, hai yêu cầu đang chờ ở 10:00: đề xuất thêm một lịch vào 10:00 là quá tải.
        request(TEN);
        request(TEN);
        AppointmentResponse third = request(NINE);

        assertConflict(() -> assignment.propose(third.id(), drA, day, TEN));
    }

    @Test
    void reProposing_releasesThePreviousSlot() {
        AppointmentResponse req = request(NINE);
        AppointmentResponse first = assignment.propose(req.id(), drA, day, TEN);

        AppointmentResponse second = assignment.propose(req.id(), drB, day, LocalTime.of(10, 30));

        assertThat(second.status()).isEqualTo(AppointmentStatus.AWAITING_CUSTOMER);
        assertThat(second.doctorUserId()).isEqualTo(drB);
        assertThat(slotRepository.findById(first.slotId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void reProposing_theSameDoctorAndTime_isAllowed() {
        AppointmentResponse req = request(NINE);
        assignment.propose(req.id(), drA, day, TEN);
        // Slot đã giữ cho chính lịch này không bị tính là bận.
        assertThat(assignment.propose(req.id(), drA, day, TEN).status()).isEqualTo(AppointmentStatus.AWAITING_CUSTOMER);
    }

    // ---- khách đồng ý / từ chối ----

    @Test
    void customer_acceptsTheProposal() {
        AppointmentResponse req = request(NINE);
        assignment.propose(req.id(), drB, day, TEN);

        AppointmentResponse r = assignment.acceptProposal(req.id(), req.customerUserId());

        assertThat(r.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(r.doctorUserId()).isEqualTo(drB);
        assertThat(r.startTime()).isEqualTo(TEN);
    }

    @Test
    void accept_byAnotherCustomer_isNotFound_andNeedsAProposal() {
        AppointmentResponse req = request(NINE);
        // Chưa có đề xuất nào.
        assertConflict(() -> assignment.acceptProposal(req.id(), req.customerUserId()));

        assignment.propose(req.id(), drB, day, TEN);
        assertThatThrownBy(() -> assignment.acceptProposal(req.id(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void customer_decliningByCancelling_givesTheDoctorBack() {
        AppointmentResponse req = request(NINE);
        AppointmentResponse proposed = assignment.propose(req.id(), drB, day, TEN);

        appointments.cancelAppointment(req.id(), req.customerUserId(), false);

        assertThat(slotRepository.findById(proposed.slotId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.AVAILABLE);
        // Bác sĩ B lại rảnh 10:00 cho người khác.
        AppointmentResponse other = request(TEN);
        assertThat(assignment.assign(other.id(), drB).status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void waitingRequests_cannotBeForcedThroughTheGenericStatusEndpoint() {
        AppointmentResponse req = request(NINE);
        assignment.propose(req.id(), drA, day, TEN);
        assertConflict(() -> appointments.updateStatus(req.id(), AppointmentStatus.CONFIRMED));
    }

    // ---- danh sách ----

    @Test
    void listAwaiting_showsOnlyWaitingRequests_inTimeOrder() {
        AppointmentResponse later = request(TEN);
        AppointmentResponse earlier = request(NINE);
        AppointmentResponse done = request(LocalTime.of(11, 0));
        assignment.assign(done.id(), drA);
        AppointmentResponse proposed = request(LocalTime.of(14, 0));
        assignment.propose(proposed.id(), drB, day, LocalTime.of(14, 30));

        List<UUID> ids = assignment.listAwaiting().stream().map(AppointmentResponse::id).toList();

        assertThat(ids).doesNotContain(done.id());
        // Trong dữ liệu test chung có thể còn lịch của test khác, nên chỉ kiểm thứ tự tương đối.
        assertThat(ids).contains(earlier.id(), later.id(), proposed.id());
        assertThat(ids.indexOf(earlier.id())).isLessThan(ids.indexOf(later.id()));
        assertThat(ids.indexOf(later.id())).isLessThan(ids.indexOf(proposed.id()));
    }

    // ---- bác sĩ nào xếp được ----

    @Test
    void availableDoctors_flagsBusyOnesAndShiftsAndLoad() {
        // BS An bận 9:00, có ca trực; BS Bình rảnh, không có ca.
        slotRepository.saveAndFlush(slot(drA, NINE, SlotStatus.BOOKED));
        shiftRepository.saveAndFlush(DoctorShift.builder().doctorUserId(drA).date(day)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0)).build());
        AppointmentResponse req = request(NINE);

        List<DoctorAvailabilityResponse> list = assignment.availableDoctors(req.id(), null, null);

        assertThat(list).hasSize(2);
        // Rảnh lên trước, dù BS An có ca trực.
        assertThat(list.get(0).doctorUserId()).isEqualTo(drB);
        assertThat(list.get(0).free()).isTrue();
        assertThat(list.get(0).onShift()).isFalse();
        assertThat(list.get(1).doctorUserId()).isEqualTo(drA);
        assertThat(list.get(1).free()).isFalse();
        assertThat(list.get(1).onShift()).isTrue();
    }

    @Test
    void availableDoctors_canLookAtAnotherTime_andSkipsBlockedDoctors() {
        blockedDoctorRepository.saveAndFlush(new BlockedDoctor(drB, Instant.now()));
        slotRepository.saveAndFlush(slot(drA, TEN, SlotStatus.BOOKED));
        AppointmentResponse req = request(NINE);

        // Xem giờ 10:00 thay vì giờ khách chọn.
        List<DoctorAvailabilityResponse> list = assignment.availableDoctors(req.id(), day, TEN);

        assertThat(list).extracting(DoctorAvailabilityResponse::doctorUserId).containsExactly(drA);
        assertThat(list.get(0).free()).isFalse();
    }

    @Test
    void availableDoctors_doesNotCountTheSlotThisAppointmentAlreadyHolds() {
        AppointmentResponse req = request(NINE);
        assignment.propose(req.id(), drA, day, TEN);

        List<DoctorAvailabilityResponse> list = assignment.availableDoctors(req.id(), day, TEN);

        assertThat(list).filteredOn(d -> d.doctorUserId().equals(drA)).allMatch(DoctorAvailabilityResponse::free);
    }

    // ---- helpers ----

    private AppointmentResponse request(LocalTime time) {
        return requests.createRequest(UUID.randomUUID(), "Bearer t",
                new AppointmentRequest(UUID.randomUUID(), day, time, null, "Ngứa tai"));
    }

    private AppointmentSlot slot(UUID doctor, LocalTime time, SlotStatus status) {
        return AppointmentSlot.builder().doctorUserId(doctor).date(day).startTime(time)
                .endTime(time.plusMinutes(30)).status(status).build();
    }

    private void assertConflict(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
