import type { ReportInterval, ReportSource } from "@/types";

/**
 * Màu biểu đồ. Recharts ghi màu vào thuộc tính SVG, nơi var(--...) không chạy ổn định trên mọi
 * trình duyệt, nên chép giá trị hex từ token trong globals.css. Đổi token thì sửa ở đây.
 */
export const CHART = {
  sales: "#2d4739", // --moss
  services: "#3a6b8a", // --info
  completed: "#2d4739", // --moss
  upcoming: "#c2ccc2", // --line-strong
  cancelled: "#a33a2e", // --danger
  noShow: "#b87514", // --amber
  grid: "#dde3dd", // --line
  axis: "#6f6455", // --bark
} as const;

export const SOURCE_LABEL: Record<ReportSource, string> = {
  sales: "doanh thu bán hàng",
  services: "doanh thu khám và đơn thuốc",
  appointments: "số liệu lịch khám",
};

const pad = (n: number) => String(n).padStart(2, "0");

/** yyyy-MM-dd theo giờ máy người dùng. */
export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function shiftDays(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  return isoDate(new Date(y, m - 1, d + days));
}

/** Số ngày tính cả hai đầu. */
export function daysBetween(from: string, to: string): number {
  const a = Date.parse(`${from}T00:00:00Z`);
  const b = Date.parse(`${to}T00:00:00Z`);
  return Math.round((b - a) / 86_400_000) + 1;
}

/** Nhãn trục của một kỳ: 05/09, 09/2026, Quý 3/2026. */
export function periodLabel(interval: ReportInterval, periodStart: string): string {
  const [y, m, d] = periodStart.split("-");
  if (interval === "DAY") return `${d}/${m}`;
  if (interval === "MONTH") return `${m}/${y}`;
  return `Quý ${Math.floor((Number(m) - 1) / 3) + 1}/${y}`;
}

const compact = new Intl.NumberFormat("vi-VN", { notation: "compact", maximumFractionDigits: 1 });

/** 1.250.000 → "1,3 Tr" — cho trục biểu đồ, nơi không có chỗ cho số đầy đủ. */
export function compactNumber(value: number): string {
  return compact.format(value);
}

/** 0.125 → "12,5%"; null → "—" (không có lịch nào thì không có tỉ lệ). */
export function percent(rate: number | null | undefined): string {
  if (rate === null || rate === undefined) return "—";
  return `${(rate * 100).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`;
}
