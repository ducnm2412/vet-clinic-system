import { http, qs } from "./client";
import type { AppointmentReport, ReportInterval, ReportSummary, RevenueReport } from "@/types";

export interface ReportRange {
  /** yyyy-MM-dd, theo giờ Việt Nam. */
  from: string;
  to: string;
}

/**
 * Báo cáo cho quản trị viên. Backend tự kiểm tra quyền ADMIN ở cả reporting-service lẫn từng
 * service nguồn — ẩn menu ở đây chỉ là tiện, không phải hàng rào.
 */
export const reportingApi = {
  summary: (range: ReportRange) => http.get<ReportSummary>(`/reporting/summary${qs({ ...range })}`),

  revenue: (range: ReportRange, interval: ReportInterval) =>
    http.get<RevenueReport>(`/reporting/revenue${qs({ ...range, interval: interval.toLowerCase() })}`),

  appointments: (range: ReportRange, interval: ReportInterval) =>
    http.get<AppointmentReport>(`/reporting/appointments${qs({ ...range, interval: interval.toLowerCase() })}`),
};
