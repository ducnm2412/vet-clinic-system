package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.service.UserDirectory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StaffListenersTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final UserDirectory directory = mock(UserDirectory.class);
    private final AppointmentStaffNoticeListener appointmentListener =
            new AppointmentStaffNoticeListener(notifications, directory);
    private final PrescriptionCreatedStaffListener prescriptionListener =
            new PrescriptionCreatedStaffListener(notifications, directory);

    private static AppointmentNotificationEvent event(String type, UUID doctor) {
        return new AppointmentNotificationEvent(type, UUID.randomUUID(), UUID.randomUUID(), "k@example.com", "Milo",
                null, null, null, LocalDate.now(), LocalTime.NOON, LocalTime.NOON.plusMinutes(30), null, null, doctor);
    }

    @Test
    void confirmedAppointment_notifiesTheDoctorOnly() {
        UUID doctor = UUID.randomUUID();

        appointmentListener.onAppointmentNotification(event("CONFIRMED", doctor));

        verify(notifications).create(eq(doctor), eq("DOCTOR_ASSIGNED"), anyString(), anyString(), anyString());
        verifyNoInteractions(directory);
    }

    @Test
    void newRequest_notifiesEveryAdminInOneGo() {
        List<UUID> admins = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(directory.idsByRole("ADMIN")).thenReturn(admins);

        appointmentListener.onAppointmentNotification(event("REQUEST_RECEIVED", null));

        verify(notifications).createForAll(eq(admins), eq("NEW_REQUEST"), anyString(), anyString(),
                eq("/admin/appointment-requests"));
        verify(notifications, never()).create(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void newRequest_alsoNotifiesEveryStaffMemberWithALinkToTheirAppointments() {
        List<UUID> staff = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(directory.idsByRole("ADMIN")).thenReturn(List.of());
        when(directory.idsByRole("STAFF")).thenReturn(staff);

        appointmentListener.onAppointmentNotification(event("REQUEST_RECEIVED", null));

        verify(notifications).createForAll(eq(staff), eq("NEW_REQUEST"), anyString(), anyString(),
                eq("/staff/appointments"));
    }

    @Test
    void newOrder_notifiesEveryStaffMemberWithALinkToOrders() {
        List<UUID> staff = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(directory.idsByRole("STAFF")).thenReturn(staff);

        new OrderPlacedStaffListener(notifications, directory).onOrderPlaced(new OrderPlacedEvent(
                UUID.randomUUID(), "ORD-1", new java.math.BigDecimal("330000"), 2, "COD", "Nguyen Van A"));

        verify(notifications).createForAll(eq(staff), eq("NEW_ORDER"), anyString(),
                org.mockito.ArgumentMatchers.contains("ORD-1"), eq("/staff/orders"));
    }

    @Test
    void proposal_notifiesNobody() {
        appointmentListener.onAppointmentNotification(event("PROPOSAL", UUID.randomUUID()));

        verifyNoInteractions(notifications, directory);
    }

    @Test
    void newPrescription_notifiesEveryStaffMember() {
        List<UUID> staff = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(directory.idsByRole("STAFF")).thenReturn(staff);

        prescriptionListener.onPrescriptionCreated(new PrescriptionCreatedEvent(UUID.randomUUID(),
                List.of(new PrescriptionCreatedEvent.Item("Amoxicillin"))));

        verify(notifications).createForAll(eq(staff), eq("PAYMENT_PENDING"), anyString(), anyString(),
                eq("/staff/payments"));
    }

    @Test
    void newDoctorAndNewStaff_getAProfileReminderLinkingToTheirOwnProfilePage() {
        var listener = new StaffAccountCreatedListener(notifications);
        UUID doctor = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        when(notifications.createOnce(any(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        listener.onStaffAccountCreated(new StaffAccountCreatedEvent(doctor, "DOCTOR"));
        listener.onStaffAccountCreated(new StaffAccountCreatedEvent(staff, "STAFF"));

        verify(notifications).createOnce(eq(doctor), eq("PROFILE_SETUP"), anyString(), anyString(), eq("/doctor/profile"));
        verify(notifications).createOnce(eq(staff), eq("PROFILE_SETUP"), anyString(), anyString(), eq("/staff/profile"));
    }

    @Test
    void roleWithoutAProfilePage_orNoUser_getsNoReminder() {
        var listener = new StaffAccountCreatedListener(notifications);

        listener.onStaffAccountCreated(new StaffAccountCreatedEvent(UUID.randomUUID(), "ADMIN"));
        listener.onStaffAccountCreated(new StaffAccountCreatedEvent(UUID.randomUUID(), null));
        listener.onStaffAccountCreated(new StaffAccountCreatedEvent(null, "DOCTOR"));

        verifyNoInteractions(notifications);
    }

    @Test
    void savingTheProfile_removesTheReminder_andIgnoresAnEventWithoutAUser() {
        var listener = new ProfileUpdatedListener(notifications);
        UUID user = UUID.randomUUID();

        listener.onProfileUpdated(new ProfileUpdatedEvent(user, "DOCTOR"));
        listener.onProfileUpdated(new ProfileUpdatedEvent(null, "DOCTOR"));

        verify(notifications).removeByType(user, "PROFILE_SETUP");
        verify(notifications, never()).removeByType(eq(null), anyString());
    }
}
