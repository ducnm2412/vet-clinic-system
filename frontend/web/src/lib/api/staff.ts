import { http, qs } from "./client";
import type {
  AttendanceRecord,
  Shift,
  ShiftCreateRequest,
  ShiftCreateResult,
  Timesheet,
} from "@/types";

export interface DateRange {
  from: string;
  to: string;
}

/** CN-38: chấm công cho chính mình. Backend lấy người chấm từ token, không nhận userId. */
export const attendanceApi = {
  checkIn: (note?: string) => http.post<AttendanceRecord>("/staff/attendance/check-in", { note }),
  checkOut: (note?: string) => http.post<AttendanceRecord>("/staff/attendance/check-out", { note }),

  /** Bản ghi hôm nay; chưa chấm công thì backend trả rỗng. */
  today: () => http.get<AttendanceRecord | null>("/staff/attendance/me/today"),

  mine: (range: DateRange) => http.get<AttendanceRecord[]>(`/staff/attendance/me${qs({ ...range })}`),

  /** CN-40: cả phòng khám, chỉ ADMIN. */
  all: (range: DateRange, userId?: string) =>
    http.get<AttendanceRecord[]>(`/staff/attendance${qs({ ...range, userId })}`),

  /** CN-48: bảng công theo kỳ, chỉ ADMIN. */
  timesheet: (range: DateRange, userId?: string) =>
    http.get<Timesheet>(`/staff/attendance/timesheet${qs({ ...range, userId })}`),
};

/** CN-39: xếp ca trực. Tạo và xoá chỉ ADMIN; xem thì nhân viên và bác sĩ cũng được. */
export const shiftApi = {
  search: (range: DateRange, userId?: string) =>
    http.get<Shift[]>(`/staff/shifts${qs({ ...range, userId })}`),

  create: (body: ShiftCreateRequest) => http.post<ShiftCreateResult>("/staff/shifts", body),

  remove: (id: string) => http.del<void>(`/staff/shifts/${id}`),
};
