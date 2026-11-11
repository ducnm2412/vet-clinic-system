// MOCK-TẠM: dữ liệu mẫu cho khu vực tài khoản khách hàng (thú cưng, lịch hẹn, đơn hàng, hồ sơ)
// để chỉnh giao diện khi KHÔNG chạy backend. Chỉ dùng khi chạy dev. XOÁ cùng các dòng "MOCK-TẠM".
import type {
  Address,
  Appointment,
  CustomerProfile,
  MedicalRecord,
  Order,
  OrderStatusHistory,
  OrderSummary,
  PageResponse,
  Pet,
} from "@/types";
import { mockProducts } from "./mock-data";

export const MOCK_ACCOUNT_ENABLED = process.env.NODE_ENV !== "production";

/** Gọi API thật; chỉ khi chạy dev và API lỗi mới trả dữ liệu mẫu. */
export async function devMock<T>(call: Promise<T>, fallback: () => T): Promise<T> {
  if (!MOCK_ACCOUNT_ENABLED) return call;
  try {
    return await call;
  } catch {
    return fallback();
  }
}

/** Ảnh tĩnh trong /public (không phải ảnh tải lên cần token). */
export const isStaticImage = (url: string | null | undefined) =>
  !!url && (url.startsWith("/img/") || url.startsWith("/mock-images/"));

const NOW = "2026-11-01T08:00:00";
const OWNER = "demo-customer";

export const mockPets: Pet[] = [
  {
    id: "pet-1", ownerUserId: OWNER, name: "Bánh Bao", species: "Chó", breed: "Corgi", gender: "MALE",
    dateOfBirth: "2023-04-12", weightKg: 11.5, allergies: null, notes: null,
    photoUrl: "/img/cho-con.jpg", createdAt: NOW, updatedAt: NOW,
  },
  {
    id: "pet-2", ownerUserId: OWNER, name: "Miu Miu", species: "Mèo", breed: "Anh lông ngắn", gender: "FEMALE",
    dateOfBirth: "2024-01-20", weightKg: 4.2, allergies: "Hải sản", notes: null,
    photoUrl: "/img/meo.jpg", createdAt: NOW, updatedAt: NOW,
  },
];

function appt(id: string, petId: string, date: string, start: string, end: string, service: string, status: Appointment["status"]): Appointment {
  return {
    id, slotId: `slot-${id}`, doctorUserId: "demo-doctor", date, startTime: start, endTime: end,
    customerUserId: OWNER, petId, serviceId: null, serviceName: service, reason: service,
    status, createdAt: NOW, updatedAt: NOW,
  };
}

export const mockAppointments: Appointment[] = [
  appt("ap-1", "pet-1", "2026-11-15", "09:00:00", "09:30:00", "Khám tổng quát", "CONFIRMED"),
  appt("ap-2", "pet-2", "2026-11-20", "15:00:00", "15:30:00", "Tiêm phòng", "PENDING"),
  appt("ap-3", "pet-1", "2026-11-03", "14:30:00", "15:00:00", "Tiêm phòng", "COMPLETED"),
  appt("ap-4", "pet-2", "2026-10-18", "10:00:00", "10:30:00", "Spa & grooming", "COMPLETED"),
  appt("ap-5", "pet-1", "2026-09-30", "08:30:00", "09:00:00", "Xét nghiệm máu", "COMPLETED"),
];

function order(code: string, status: OrderSummary["status"], items: number, total: number, date: string, paid: boolean): OrderSummary {
  return {
    id: `od-${code}`, orderCode: `#DH${code}`, status, channel: "ONLINE", paymentMethod: "COD",
    paymentStatus: paid ? "PAID" : "UNPAID", totalItems: items, total, recipientName: "Khách hàng demo",
    createdAt: `${date}T10:15:00`, refundRequired: false,
  };
}

export const mockOrders: OrderSummary[] = [
  order("001234", "SHIPPING", 3, 520000, "2026-11-10", false),
  order("001233", "COMPLETED", 2, 320000, "2026-11-05", true),
  order("001232", "CANCELLED", 1, 280000, "2026-11-01", false),
  order("001231", "PENDING", 4, 765000, "2026-10-28", false),
  order("001230", "COMPLETED", 1, 185000, "2026-10-20", true),
  order("001229", "CONFIRMED", 2, 410000, "2026-10-15", false),
];

export function mockOrderPage(page: number, size: number): PageResponse<OrderSummary> {
  const content = mockOrders.slice(page * size, page * size + size);
  const totalPages = Math.max(1, Math.ceil(mockOrders.length / size));
  return { content, page, size, totalElements: mockOrders.length, totalPages, last: page >= totalPages - 1 };
}

export const mockProfile: CustomerProfile = {
  id: "cp-1", userId: OWNER, phone: "0929 383 886", dateOfBirth: "1998-06-15", gender: "FEMALE",
  photoUrl: null, createdAt: NOW, updatedAt: NOW,
};

export const mockAddresses: Address[] = [
  {
    id: "ad-1", line1: "12 Nguyễn Văn Linh", line2: null, ward: "Phường Hải Châu", city: "Đà Nẵng", isDefault: true,
    createdAt: NOW, updatedAt: NOW,
  },
];

// ---------------------------------------------------------------------------
// Chi tiết (thú cưng, đơn hàng) + các phần backend CHƯA có API (họ tên). Các phần chưa có API trả null ở production để UI hiện "Sắp ra mắt".
// ---------------------------------------------------------------------------

export function mockPet(id: string): Pet {
  const pet = mockPets.find((p) => p.id === id);
  if (!pet) throw new Error("not found");
  return pet;
}

export function mockPetHistory(petId: string): MedicalRecord[] {
  return mockAppointments
    .filter((a) => a.petId === petId && a.status === "COMPLETED")
    .map((a, i) => ({
      id: `mr-${a.id}`, appointmentId: a.id, petId, customerUserId: OWNER, doctorUserId: a.doctorUserId,
      diagnosis: i === 0 ? "Sức khoẻ tốt, đã tiêm nhắc mũi 7 bệnh" : "Viêm da nhẹ do nấm",
      treatment: i === 0 ? "Tiêm vắc-xin 7 bệnh, hẹn nhắc lại sau 12 tháng" : "Thuốc bôi kháng nấm 7 ngày",
      notes: null, status: "RECEIVED", prescriptionItems: [], createdAt: `${a.date}T${a.startTime}`, updatedAt: NOW,
    }));
}

/** Mỗi đơn mẫu lấy vài sản phẩm mẫu cố định theo mã đơn. */
function itemsFor(o: OrderSummary) {
  const seed = Number(o.orderCode.replace(/\D/g, "")) % 50;
  return Array.from({ length: Math.min(o.totalItems, 3) }, (_, i) => {
    const p = mockProducts[(seed * 7 + i * 13) % mockProducts.length];
    return { productId: p.id, sku: p.sku, productName: p.name, unitPrice: p.price, quantity: 1, lineTotal: p.price };
  });
}

export function mockOrderDetail(id: string): Order {
  const o = mockOrders.find((x) => x.id === id);
  if (!o) throw new Error("not found");
  const items = itemsFor(o);
  const subtotal = items.reduce((s, i) => s + i.lineTotal, 0);
  return {
    id: o.id, orderCode: o.orderCode, userId: OWNER, status: o.status, channel: o.channel,
    paymentMethod: o.paymentMethod, paymentStatus: o.paymentStatus, recipientName: "Khách hàng demo",
    recipientPhone: "0929383886", shippingAddress: "12 Nguyễn Văn Linh, Phường Hải Châu, Đà Nẵng", note: null,
    subtotal, shippingFee: 30000, examPaymentId: null, examAmount: 0, total: subtotal + 30000, items,
    createdAt: o.createdAt, confirmedAt: o.status !== "PENDING" ? o.createdAt : null,
    completedAt: o.status === "COMPLETED" ? o.createdAt : null, paidAt: o.paymentStatus === "PAID" ? o.createdAt : null,
    paymentExpiresAt: null, transferReference: null,
    cancelledAt: o.status === "CANCELLED" ? o.createdAt : null,
    cancelReason: o.status === "CANCELLED" ? "Khách đổi ý" : null, refundRequired: false,
  };
}

export function mockOrderHistory(id: string): OrderStatusHistory[] {
  const o = mockOrders.find((x) => x.id === id);
  if (!o) return [];
  const flow: OrderSummary["status"][] =
    o.status === "CANCELLED" ? ["PENDING", "CANCELLED"] : ["PENDING", "CONFIRMED", "SHIPPING", "COMPLETED"];
  const end = o.status === "CANCELLED" ? flow.length : flow.indexOf(o.status) + 1;
  return flow.slice(0, end).map((s, i) => ({
    fromStatus: i === 0 ? null : flow[i - 1], toStatus: s, changedBy: null, note: null,
    createdAt: `${o.createdAt.slice(0, 10)}T${String(9 + i * 2).padStart(2, "0")}:00:00`,
  }));
}

/** Ảnh sản phẩm mẫu theo id (dùng cho thẻ đơn hàng). */
export function mockProductImage(productId: string): string | null {
  return mockProducts.find((p) => p.id === productId)?.imageUrl ?? null;
}

/* ---------- Chưa có API: chỉ dev mới có dữ liệu ---------- */

/** Họ tên: hồ sơ khách chưa có trường này. */
export const mockFullName: string | null = MOCK_ACCOUNT_ENABLED ? "Nguyễn Thị Lan" : null;
