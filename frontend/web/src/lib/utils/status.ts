import type {
  AppointmentStatus,
  OrderStatus,
  PaymentStatus,
  PrescriptionStatus,
  StockStatus,
} from "@/types";

/**
 * Mỗi trạng thái luôn đi kèm chấm màu **và** chữ — không bao giờ chỉ dùng màu, để người
 * mù màu và người xem trên màn hình kém tương phản vẫn đọc được.
 * `dot` là màu chấm, `tint` là nền nhạt khi cần làm nổi cả hàng.
 */
export interface StatusStyle {
  label: string;
  dot: string;
  tint: string;
}

export const ORDER_STATUS: Record<OrderStatus, StatusStyle> = {
  PENDING: { label: "Chờ xác nhận", dot: "bg-amber", tint: "bg-amber-wash text-ink" },
  CONFIRMED: { label: "Đã xác nhận", dot: "bg-info", tint: "bg-info-wash text-ink" },
  SHIPPING: { label: "Đang giao", dot: "bg-info", tint: "bg-info-wash text-ink" },
  COMPLETED: { label: "Hoàn tất", dot: "bg-moss", tint: "bg-moss-wash text-ink" },
  CANCELLED: { label: "Đã huỷ", dot: "bg-danger", tint: "bg-danger-wash text-ink" },
};

export const APPOINTMENT_STATUS: Record<AppointmentStatus, StatusStyle> = {
  PENDING: { label: "Chờ xác nhận", dot: "bg-amber", tint: "bg-amber-wash text-ink" },
  CONFIRMED: { label: "Đã xác nhận", dot: "bg-info", tint: "bg-info-wash text-ink" },
  COMPLETED: { label: "Đã khám", dot: "bg-moss", tint: "bg-moss-wash text-ink" },
  CANCELLED: { label: "Đã huỷ", dot: "bg-danger", tint: "bg-danger-wash text-ink" },
  NO_SHOW: { label: "Không đến", dot: "bg-bark", tint: "bg-paper text-ink-soft" },
};

export const PRESCRIPTION_STATUS: Record<PrescriptionStatus, StatusStyle> = {
  PENDING: { label: "Chờ thu tiền", dot: "bg-amber", tint: "bg-amber-wash text-ink" },
  PAID: { label: "Đã thu tiền", dot: "bg-info", tint: "bg-info-wash text-ink" },
  RECEIVED: { label: "Đã phát thuốc", dot: "bg-moss", tint: "bg-moss-wash text-ink" },
};

export const PAYMENT_STATUS: Record<PaymentStatus, StatusStyle> = {
  PENDING_AMOUNT: { label: "Chờ nhập số tiền", dot: "bg-amber", tint: "bg-amber-wash text-ink" },
  PENDING_PAYMENT: { label: "Chờ khách trả", dot: "bg-info", tint: "bg-info-wash text-ink" },
  COMPLETED: { label: "Đã thu", dot: "bg-moss", tint: "bg-moss-wash text-ink" },
  CANCELLED: { label: "Đã huỷ", dot: "bg-danger", tint: "bg-danger-wash text-ink" },
};

export const STOCK_STATUS: Record<StockStatus, StatusStyle> = {
  IN_STOCK: { label: "Còn hàng", dot: "bg-moss", tint: "bg-moss-wash text-ink" },
  LOW_STOCK: { label: "Sắp hết", dot: "bg-amber", tint: "bg-amber-wash text-ink" },
  OUT_OF_STOCK: { label: "Hết hàng", dot: "bg-danger", tint: "bg-danger-wash text-ink" },
};

/**
 * Dải màu định danh loài — điểm nhấn thị giác duy nhất của hệ thống, mượn từ tab màu
 * trên bìa hồ sơ bệnh án giấy. Loài lạ rơi về màu trung tính thay vì bịa màu mới.
 */
const SPECIES_STRIPE: Record<string, string> = {
  chó: "bg-[#8a6d3b]",
  mèo: "bg-[#5f7d8c]",
  chim: "bg-[#7b8f4e]",
  thỏ: "bg-[#9b7b8a]",
  hamster: "bg-[#a8813f]",
  "bò sát": "bg-[#5d7a5a]",
  cá: "bg-[#4f7c86]",
};

export function speciesStripe(species: string | null | undefined): string {
  if (!species) return "bg-line-strong";
  return SPECIES_STRIPE[species.trim().toLowerCase()] ?? "bg-line-strong";
}
