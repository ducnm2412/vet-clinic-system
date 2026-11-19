/**
 * staff-service (CN-38 → 41, CN-48): chấm công và ca trực.
 *
 * Ca trực của bác sĩ quyết định giờ khám mở cho khách — booking-service nghe sự kiện từ
 * staff-service. Bác sĩ không có ca thì ngày đó khách không đặt được.
 */
export interface Shift {
  id: string;
  userId: string;
  /** yyyy-MM-dd */
  date: string;
  /** HH:mm:ss */
  startTime: string;
  endTime: string;
  note: string | null;
}

export interface ShiftCreateRequest {
  userId: string;
  /** Xếp nhiều ngày một lượt, tối đa 31 ngày. */
  dates: string[];
  /** Bỏ trống thì lấy giờ làm việc cố định của phòng khám (08:00–17:00). */
  startTime?: string;
  endTime?: string;
  note?: string;
}

export interface ShiftCreateResult {
  created: Shift[];
  /** Ngày đã có sẵn đúng ca này từ trước — không phải lỗi. */
  skipped: string[];
}

/** Ngày nghỉ do admin ghi tay khi có người báo nghỉ. */
export interface Leave {
  id: string;
  userId: string;
  /** yyyy-MM-dd */
  date: string;
  reason: string | null;
}

export interface LeaveCreateRequest {
  userId: string;
  dates: string[];
  reason?: string;
}

export interface LeaveCreateResult {
  created: Leave[];
  /** Ngày đã ghi nghỉ từ trước. */
  skipped: string[];
  /** Số ca đã xếp trong các ngày nghỉ bị gỡ. */
  removedShifts: number;
}

export interface AttendanceRecord {
  id: string;
  userId: string;
  date: string;
  checkInAt: string;
  checkOutAt: string | null;
  /** Chưa ra ca thì bằng 0, không phải "làm 0 phút". */
  workedMinutes: number;
  /** Ca được xếp hôm đó (đầu ca đầu tiên đến cuối ca cuối cùng); null nghĩa là đi làm ngoài ca. */
  shiftStart: string | null;
  shiftEnd: string | null;
  /** Số phút vào muộn so với ca, chỉ tính khi quá dung sai 10 phút; 0 = đúng giờ hoặc ngoài ca. */
  lateMinutes: number;
  /** Số phút ra sớm so với ca (quá dung sai); 0 = đúng giờ, ngoài ca, hoặc chưa ra ca. */
  earlyLeaveMinutes: number;
  note: string | null;
  /** Giờ ra do hệ thống tự điền vì quên ra ca; giờ công tính theo hết ca được xếp. */
  autoClosed: boolean;
}

export interface TimesheetRow {
  userId: string;
  daysWorked: number;
  totalMinutes: number;
  daysMissingCheckOut: number;
  shiftsAssigned: number;
  daysLate: number;
  daysEarlyLeave: number;
  /** Ngày có ca được xếp mà không chấm công; chỉ tính các ngày đã qua. */
  daysAbsent: number;
}

/** Một ngày có ca được xếp nhưng không có bản ghi chấm công. */
export interface AttendanceAbsence {
  userId: string;
  date: string;
  shiftStart: string;
  shiftEnd: string;
}

export interface Timesheet {
  from: string;
  to: string;
  rows: TimesheetRow[];
}
