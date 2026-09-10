const vnd = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
});

export function formatPrice(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return "—";
  const n = typeof value === "string" ? Number(value) : value;
  return Number.isFinite(n) ? vnd.format(n) : "—";
}

const dateTimeFmt = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const dateFmt = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : dateTimeFmt.format(d);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const d = new Date(value.length <= 10 ? `${value}T00:00:00` : value);
  return Number.isNaN(d.getTime()) ? "—" : dateFmt.format(d);
}

/** Backend trả LocalTime dạng "09:00:00" — bỏ phần giây cho gọn. */
export function formatTime(value: string | null | undefined): string {
  if (!value) return "—";
  return value.slice(0, 5);
}

/** Ngày hôm nay theo định dạng backend nhận (LocalDate). */
export function todayISO(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Tuổi thú cưng, nói theo cách chủ nuôi hay dùng chứ không phải số thập phân. */
export function formatAge(dateOfBirth: string | null | undefined): string {
  if (!dateOfBirth) return "Chưa rõ tuổi";
  const born = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(born.getTime())) return "Chưa rõ tuổi";
  const months = Math.max(
    0,
    (new Date().getFullYear() - born.getFullYear()) * 12 + new Date().getMonth() - born.getMonth(),
  );
  if (months < 1) return "Dưới 1 tháng";
  if (months < 24) return `${months} tháng`;
  return `${Math.floor(months / 12)} tuổi`;
}
