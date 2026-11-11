import type { Pet } from "./profile";

export type AppointmentStatus =
  | "PENDING"
  /** Khách đã gửi yêu cầu theo giờ mong muốn; chờ phòng khám xếp bác sĩ. */
  | "AWAITING_ASSIGNMENT"
  /** Phòng khám đề xuất giờ khác; chờ khách đồng ý hoặc huỷ. */
  | "AWAITING_CUSTOMER"
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
  /** VD-21: không bắt buộc — khách chưa rõ cần gì thì mô tả ở ô lý do khám. */
  serviceId?: string;
  reason?: string;
}

/** booking-service: ClinicServiceResponse — danh mục dịch vụ của phòng khám (VD-21). */
export interface ClinicService {
  id: string;
  /** Ổn định theo thời gian: giao diện chọn biểu tượng theo slug. */
  slug: string;
  name: string;
  description: string | null;
  durationMinutes: number;
  referencePrice: number | null;
  active: boolean;
  displayOrder: number;
}

export interface ClinicServiceRequest {
  slug: string;
  name: string;
  description?: string;
  durationMinutes?: number;
  referencePrice?: number;
  displayOrder?: number;
}

export interface Appointment {
  id: string;
  /** null khi lịch còn chờ xếp bác sĩ (AWAITING_*): chưa có khung giờ của ai. */
  slotId: string | null;
  /**
   * Bác sĩ phụ trách, lấy từ slot (VD-16). Tra tên qua useDoctorDirectory. null khi chưa xếp.
   * Chưa có slot thì `date`/`startTime` là giờ khách mong muốn.
   */
  doctorUserId: string | null;
  date: string;
  startTime: string;
  endTime: string;
  customerUserId: string;
  petId: string;
  /** VD-21: dịch vụ khách chọn; null với lịch hẹn cũ hoặc khách không chọn. */
  serviceId: string | null;
  serviceName: string | null;
  reason: string | null;
  status: AppointmentStatus;
  createdAt: string;
  updatedAt: string;
  /** Giờ phòng khám đề xuất khi không xếp được đúng giờ khách chọn (AWAITING_CUSTOMER). */
  proposedDate: string | null;
  proposedStartTime: string | null;
  /**
   * Giờ khách chọn lúc gửi yêu cầu; null với lịch đặt kiểu cũ. Khi có đề xuất, `date`/`startTime`
   * là giờ đề xuất còn đây là giờ ban đầu.
   */
  preferredDate: string | null;
  preferredStartTime: string | null;
}

/** Một bác sĩ trong danh sách admin chọn khi xếp lịch. */
export interface DoctorAvailability {
  doctorUserId: string;
  fullName: string | null;
  specialty: string | null;
  /** Chưa có lịch khám vào giờ đó — chọn được. */
  free: boolean;
  /** Giờ đó nằm trong ca trực đã xếp; chỉ để gợi ý, bác sĩ ngoài ca vẫn xếp được. */
  onShift: boolean;
  /** Số lịch bác sĩ đã có trong ngày. */
  dayLoad: number;
}

export interface ProposeTimeRequest {
  doctorUserId: string;
  date: string;
  startTime: string;
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

/**
 * Một mốc giờ khách chọn được khi gửi yêu cầu đặt lịch. `remaining` là số yêu cầu còn nhận được
 * ở mốc đó (0 = đã đầy hoặc đã qua).
 */
export interface ClinicHour {
  startTime: string;
  endTime: string;
  available: boolean;
  remaining: number;
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
