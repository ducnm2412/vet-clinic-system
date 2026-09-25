import type { Pet } from "./profile";

export type AppointmentStatus =
  | "PENDING"
  | "CONFIRMED"
  | "CANCELLED"
  | "COMPLETED"
  | "NO_SHOW";

/** Trạng thái đơn thuốc: tạo ra là PENDING, thu tiền xong thành PAID, phát thuốc thành RECEIVED. */
export type PrescriptionStatus = "PENDING" | "PAID" | "RECEIVED";

/**
 * Khách chỉ chọn thú cưng và giờ — hệ thống tự gán slot, không chọn bác sĩ.
 * Bác sĩ được xếp trả về trong `Appointment.doctorUserId`.
 */
export interface AppointmentRequest {
  petId: string;
  date: string;
  startTime: string;
  reason?: string;
}

export interface Appointment {
  id: string;
  slotId: string;
  /** Bác sĩ phụ trách, lấy từ slot (VD-16). Tra tên qua useDoctorDirectory. */
  doctorUserId: string;
  date: string;
  startTime: string;
  endTime: string;
  customerUserId: string;
  petId: string;
  reason: string | null;
  status: AppointmentStatus;
  createdAt: string;
  updatedAt: string;
}

/** Bản chi tiết kèm hồ sơ thú cưng, dùng cho màn hình khám của bác sĩ. */
export interface AppointmentDetail extends Appointment {
  pet: Pet | null;
}

export interface AvailableTime {
  startTime: string;
  endTime: string;
  availableCount: number;
}

export type SlotStatus = "AVAILABLE" | "BOOKED" | "CANCELLED";

/** Khung giờ khám của một bác sĩ trong một ngày. */
export interface AppointmentSlot {
  id: string;
  doctorUserId: string;
  date: string;
  startTime: string;
  endTime: string;
  status: SlotStatus;
  createdAt: string;
  updatedAt: string;
}

/** Khi khung giờ đã kín, backend trả 409 kèm tối đa 3 gợi ý thay thế. */
export interface SuggestedSlot {
  date: string;
  startTime: string;
  endTime: string;
}

export interface PrescriptionItem {
  id: string;
  medicationName: string;
  dosage: string;
  frequency: string;
  durationDays: number | null;
  notes: string | null;
}

export interface PrescriptionItemRequest {
  medicationName: string;
  dosage: string;
  frequency: string;
  durationDays?: number;
  notes?: string;
}

export interface MedicalRecord {
  id: string;
  appointmentId: string;
  /** pet-service chép sẵn ba trường này lúc lập bệnh án — xem services/pet-service/README.md. */
  petId: string;
  customerUserId: string;
  doctorUserId: string;
  diagnosis: string;
  treatment: string | null;
  notes: string | null;
  status: PrescriptionStatus;
  prescriptionItems: PrescriptionItem[];
  createdAt: string;
  updatedAt: string;
}

export interface MedicalRecordRequest {
  diagnosis: string;
  treatment?: string;
  notes?: string;
  prescriptionItems?: PrescriptionItemRequest[];
}

export interface AppointmentFilter {
  date?: string;
  status?: AppointmentStatus;
  doctorUserId?: string;
}
