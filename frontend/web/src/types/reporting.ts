/**
 * reporting-service (CN-46, CN-47, CN-49). Chỉ ADMIN.
 *
 * Quy ước quan trọng: một nguồn không trả lời thì field của nó là `null` và tên nguồn nằm trong
 * `unavailable`. `0` nghĩa là đã hỏi và thật sự không có. Giao diện phải hiện "—" cho null,
 * không được coi null là 0.
 */
export type ReportInterval = "DAY" | "MONTH" | "QUARTER";

/** sales = order-service, services = payment-service, appointments = booking-service. */
export type ReportSource = "sales" | "services" | "appointments";

export interface RevenuePoint {
  periodStart: string;
  periodEnd: string;
  sales: number | null;
  services: number | null;
  total: number | null;
}

export interface RevenueReport {
  from: string;
  to: string;
  interval: ReportInterval;
  points: RevenuePoint[];
  totals: {
    sales: number | null;
    services: number | null;
    total: number | null;
    ordersCompleted: number | null;
    payments: number | null;
  };
  unavailable: ReportSource[];
}

export interface AppointmentCounts {
  total: number;
  pending: number;
  confirmed: number;
  completed: number;
  cancelled: number;
  noShow: number;
}

export interface AppointmentReport {
  from: string;
  to: string;
  interval: ReportInterval;
  totals: AppointmentCounts;
  /** 0..1; null khi không có lịch nào trong khoảng. */
  cancellationRate: number | null;
  noShowRate: number | null;
  points: { periodStart: string; periodEnd: string; counts: AppointmentCounts }[];
  byDoctor: {
    doctorUserId: string;
    doctorName: string | null;
    counts: AppointmentCounts;
    cancellationRate: number | null;
  }[];
}

export interface ReportSummary {
  from: string;
  to: string;
  salesRevenue: number | null;
  serviceRevenue: number | null;
  totalRevenue: number | null;
  orders: { created: number; completed: number; cancelled: number } | null;
  appointments: AppointmentCounts | null;
  cancellationRate: number | null;
  unavailable: ReportSource[];
}
