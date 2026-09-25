import { ApiError, http, qs } from "./client";
import type {
  Appointment,
  AppointmentDetail,
  AppointmentFilter,
  AppointmentRequest,
  AppointmentSlot,
  AppointmentStatus,
  AvailableTime,
  ClinicService,
  ClinicServiceRequest,
  SuggestedSlot,
} from "@/types";

export const bookingApi = {
  /**
   * Khách chỉ chọn thú cưng và giờ — không chọn bác sĩ, hệ thống tự gán slot. Bác sĩ được
   * xếp nằm trong `doctorUserId` của response.
   * Khi khung giờ đã kín, backend trả 409 kèm tối đa 3 gợi ý; dùng `suggestionsFrom`
   * để lấy chúng ra thay vì chỉ hiện thông báo lỗi chung.
   */
  create: (body: AppointmentRequest) => http.post<Appointment>("/booking/appointments", body),

  mine: () => http.get<Appointment[]>("/booking/appointments/me"),

  /** Lịch của chính bác sĩ đang đăng nhập. */
  forMeAsDoctor: (date?: string) =>
    http.get<Appointment[]>(`/booking/appointments/doctor/me${qs({ date })}`),

  /** Tra cứu chung cho nhân viên và quản trị. */
  search: (filter: AppointmentFilter = {}) =>
    http.get<Appointment[]>(
      `/booking/appointments${qs({
        date: filter.date,
        status: filter.status,
        doctorUserId: filter.doctorUserId,
      })}`,
    ),

  byId: (id: string) => http.get<AppointmentDetail>(`/booking/appointments/${id}`),

  cancel: (id: string) => http.put<Appointment>(`/booking/appointments/${id}/cancel`),

  updateStatus: (id: string, status: AppointmentStatus) =>
    http.put<Appointment>(`/booking/appointments/${id}/status`, { status }),

  availableTimes: (date: string) =>
    http.get<AvailableTime[]>(`/booking/available-times${qs({ date })}`),

  /** Sinh khung giờ cho một bác sĩ trong một ngày. Scheduler cũng chạy việc này định kỳ. */
  generateSlots: (doctorUserId: string, date: string) =>
    http.post<AppointmentSlot[]>("/booking/slots/generate", { doctorUserId, date }),
};

/**
 * VD-21: danh mục dịch vụ của phòng khám. Danh sách công khai — trang chủ và trang đặt lịch đọc
 * được khi khách chưa đăng nhập; nhân viên và quản trị thấy thêm dịch vụ đã ngừng.
 */
export const clinicServiceApi = {
  /**
   * Không đánh dấu publicRoute: khách chưa đăng nhập vẫn gọi được (backend cho phép), còn nhân
   * viên và quản trị cần gửi token mới thấy dịch vụ đã ngừng cung cấp.
   */
  list: () => http.get<ClinicService[]>("/booking/services"),
  create: (body: ClinicServiceRequest) => http.post<ClinicService>("/booking/services", body),
  update: (id: string, body: ClinicServiceRequest) =>
    http.put<ClinicService>(`/booking/services/${id}`, body),
  /** Không có xoá: lịch hẹn cũ vẫn trỏ tới dịch vụ này. */
  hide: (id: string) => http.put<ClinicService>(`/booking/services/${id}/hide`),
  unhide: (id: string) => http.put<ClinicService>(`/booking/services/${id}/unhide`),
};

/**
 * Lấy danh sách khung giờ thay thế từ lỗi 409 khi slot đã kín.
 * Trả mảng rỗng nếu lỗi không phải dạng đó.
 */
export function suggestionsFrom(error: unknown): SuggestedSlot[] {
  if (!(error instanceof ApiError) || error.status !== 409) return [];
  const body = error.body as { suggestions?: SuggestedSlot[] } | null;
  return body?.suggestions ?? [];
}
