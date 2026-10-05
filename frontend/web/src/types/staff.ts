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
  startTime: string;
  endTime: string;
  note?: string;
}

export interface ShiftCreateResult {
  created: Shift[];
  /** Ngày đã có sẵn đúng ca này từ trước — không phải lỗi. */
  skipped: string[];
}

export interface AttendanceRecord {
  id: string;
  userId: string;
  date: string;
  checkInAt: string;
  checkOutAt: string | null;
  /** Chưa ra ca thì bằng 0, không phải "làm 0 phút". */
  workedMinutes: number;
  /** Ca được xếp hôm đó; null nghĩa là đi làm ngoài ca. */
  shiftStart: string | null;
  shiftEnd: string | null;
  note: string | null;
}

export interface TimesheetRow {
  userId: string;
  daysWorked: number;
  totalMinutes: number;
  daysMissingCheckOut: number;
  shiftsAssigned: number;
}

export interface Timesheet {
  from: string;
  to: string;
  rows: TimesheetRow[];
}
