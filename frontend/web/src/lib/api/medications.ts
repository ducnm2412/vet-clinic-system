import { http, qs } from "./client";
import type { Medication, MedicationRequest, MedicationStockMovement, StockAdjustmentRequest } from "@/types";

/** Backend giới hạn tối đa 50 dòng mỗi lần (MedicationService.MAX_LIMIT) và chưa có phân trang. */
export const MEDICATION_MAX_LIMIT = 50;

/**
 * Danh mục thuốc dùng chung của phòng khám (product-service). DOCTOR và ADMIN cùng dùng và quản lý;
 * kho thuốc (inventory, nhập kho, lịch sử) dành cho STAFF và ADMIN.
 */
export const medicationApi = {
  /**
   * Gợi ý theo tên, không phân biệt dấu và hoa/thường; tên bắt đầu bằng từ khoá xếp trước.
   * Mặc định chỉ trả thuốc đang dùng — màn quản lý xin thêm thuốc đã ẩn bằng includeInactive.
   */
  search: (q?: string, opts: { includeInactive?: boolean; limit?: number } = {}) =>
    http.get<Medication[]>(
      `/medications${qs({ q: q?.trim(), includeInactive: opts.includeInactive, limit: opts.limit })}`,
    ),
  create: (body: MedicationRequest) => http.post<Medication>("/medications", body),
  update: (id: string, body: MedicationRequest) => http.put<Medication>(`/medications/${id}`, body),
  /** "Xoá" là ẩn khỏi gợi ý, không xoá cứng — đơn mẫu trỏ vào thuốc. Gọi lại nhiều lần không lỗi. */
  hide: (id: string) => http.del<void>(`/medications/${id}`),
  /** Bật lại bằng PUT: backend ghi đè unit/caution/giá theo body nên phải gửi lại đủ các trường. */
  unhide: (m: Medication) =>
    http.put<Medication>(`/medications/${m.id}`, {
      name: m.name,
      unit: m.unit ?? undefined,
      caution: m.caution ?? undefined,
      unitPrice: m.unitPrice ?? undefined,
      active: true,
    } satisfies MedicationRequest),

  // ---------- Kho thuốc (STAFF, ADMIN) ----------
  /** Danh sách thuốc kèm tồn cho màn kho (tối đa 500 dòng); `lowOnly` chỉ lấy thuốc sắp hết hoặc đã hết. */
  inventory: (opts: { q?: string; lowOnly?: boolean; includeInactive?: boolean } = {}) =>
    http.get<Medication[]>(
      `/medications/inventory${qs({ q: opts.q?.trim(), lowOnly: opts.lowOnly, includeInactive: opts.includeInactive })}`,
    ),
  adjustStock: (id: string, body: StockAdjustmentRequest) =>
    http.post<Medication>(`/medications/${id}/stock`, body),
  setLowStockThreshold: (id: string, threshold: number) =>
    http.put<Medication>(`/medications/${id}/low-stock-threshold`, { threshold }),
  stockMovements: (id: string) => http.get<MedicationStockMovement[]>(`/medications/${id}/stock-movements`),
};
