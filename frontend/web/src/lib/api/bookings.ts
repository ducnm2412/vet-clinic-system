import { ApiError, http, qs } from "./client";
import { mockServices, withMock } from "./mock-data"; // MOCK-TẠM
import { devMock, mockAppointments } from "./mock-account"; // MOCK-TẠM
import type {
  Appointment,
  AppointmentDetail,
  AppointmentFilter,
  AppointmentRequest,
  AppointmentSlot,
  AppointmentStatus,
  AvailableTime,
  ClinicHour,
  ClinicService,
  ClinicServiceRequest,
  DoctorAvailability,
  ProposeTimeRequest,
  SuggestedSlot,
} from "@/types";

export const bookingApi = {
  /**
   * Khách chỉ chọn thú cưng và giờ — không chọn bác sĩ, hệ thống tự gán slot. Bác sĩ được
   * xếp nằm trong `doctorUserId` của response.
   * Khi khung giờ đã kín, backend trả 409 kèm tối đa 3 gợi ý; dùng `suggestionsFrom`
   * để lấy chúng ra thay vì chỉ hiện thông báo lỗi chung.
   */
  create: (body: AppointmentRequest) =>
    http.post<Appointment>("/booking/appointments", body),

  /**
   * Gửi yêu cầu đặt lịch theo giờ mong muốn. Chưa có bác sĩ và chưa giữ khung giờ nào: phòng
   * khám xếp bác sĩ sau (AWAITING_ASSIGNMENT). Lỗi 409 nghĩa là khung giờ đã đủ lịch, bé đã có
   * lịch giờ đó, hoặc khách đang có quá nhiều yêu cầu chờ — message của backend nói rõ lý do.
   */
  request: (body: AppointmentRequest) =>
    http.post<Appointment>("/booking/appointments/requests", body),

  /** Các mốc giờ chọn được trong ngày, kèm số chỗ còn lại. Ngoài 30 ngày tới trả mảng rỗng. */
  clinicHours: (date: string) =>
    http.get<ClinicHour[]>(`/booking/clinic-hours${qs({ date })}`),

  /** ADMIN: lịch đang chờ xếp bác sĩ hoặc chờ khách đồng ý, theo giờ khám tăng dần. */
  awaiting: () => http.get<Appointment[]>("/booking/appointments/awaiting"),

  /**
   * ADMIN: bác sĩ xếp được vào lịch này. Không truyền `date`/`time` thì xem đúng giờ khách chọn;
   * truyền để xem một giờ khác khi tính đề xuất. Bác sĩ đang bận vẫn có mặt, kèm `free: false`.
   */
  availableDoctors: (id: string, when?: { date: string; time: string }) =>
    http.get<DoctorAvailability[]>(
      `/booking/appointments/${id}/available-doctors${qs({ date: when?.date, time: when?.time })}`,
    ),

  /** ADMIN: xếp bác sĩ đúng giờ khách chọn — lịch thành "Đã xác nhận". */
  assign: (id: string, doctorUserId: string) =>
    http.put<Appointment>(`/booking/appointments/${id}/assign`, {
      doctorUserId,
    }),

  /** ADMIN: đề xuất giờ khác (đã giữ sẵn bác sĩ), chờ khách đồng ý hoặc huỷ. */
  propose: (id: string, body: ProposeTimeRequest) =>
    http.put<Appointment>(`/booking/appointments/${id}/propose`, body),

  /** CUSTOMER: đồng ý giờ phòng khám đề xuất. Không đồng ý thì gọi `cancel`. */
  acceptProposal: (id: string) =>
    http.put<Appointment>(`/booking/appointments/${id}/accept-proposal`),

  mine: () => devMock(http.get<Appointment[]>("/booking/appointments/me"), () => mockAppointments), // MOCK-TẠM

  /**
   * CN-19: lễ tân đặt lịch hộ khách đang đứng ở quầy. Backend đối chiếu con vật có đúng của khách
   * đã chọn không — nhân viên không phải chủ nên không đi đường /pets/me được.
   */
  createWalkIn: (customerUserId: string, appointment: AppointmentRequest) =>
    http.post<Appointment>("/booking/appointments/walk-in", {
      customerUserId,
      appointment,
    }),

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

  byId: (id: string) =>
    http.get<AppointmentDetail>(`/booking/appointments/${id}`),

  cancel: (id: string) =>
    http.put<Appointment>(`/booking/appointments/${id}/cancel`),

  updateStatus: (id: string, status: AppointmentStatus) =>
    http.put<Appointment>(`/booking/appointments/${id}/status`, { status }),

  availableTimes: (date: string) =>
    http.get<AvailableTime[]>(`/booking/available-times${qs({ date })}`),

  /** Sinh khung giờ cho một bác sĩ trong một ngày. Scheduler cũng chạy việc này định kỳ. */
  generateSlots: (doctorUserId: string, date: string) =>
    http.post<AppointmentSlot[]>("/booking/slots/generate", {
      doctorUserId,
      date,
    }),
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
  list: () =>
    withMock(
      http.get<ClinicService[]>("/booking/services"),
      () => mockServices,
    ), // MOCK-TẠM
  create: (body: ClinicServiceRequest) =>
    http.post<ClinicService>("/booking/services", body),
  update: (id: string, body: ClinicServiceRequest) =>
    http.put<ClinicService>(`/booking/services/${id}`, body),
  /** Không có xoá: lịch hẹn cũ vẫn trỏ tới dịch vụ này. */
  hide: (id: string) => http.put<ClinicService>(`/booking/services/${id}/hide`),
  unhide: (id: string) =>
    http.put<ClinicService>(`/booking/services/${id}/unhide`),
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
