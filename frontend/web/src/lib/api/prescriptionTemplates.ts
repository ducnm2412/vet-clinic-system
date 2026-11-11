import { http, qs } from "./client";
import type {
  PrescriptionTemplate,
  PrescriptionTemplateRequest,
  PrescriptionTemplateSummary,
} from "@/types";

/** Backend giới hạn số thuốc trong một mẫu (PrescriptionTemplateRequest). */
export const TEMPLATE_MAX_ITEMS = 30;

/**
 * Đơn mẫu dùng chung của phòng khám (pet-service). DOCTOR và ADMIN cùng đọc và quản lý.
 * Mẫu chỉ để điền sẵn vào bệnh án: bệnh án lưu như cũ, không giữ id mẫu.
 */
export const prescriptionTemplateApi = {
  /** Lọc theo tên mẫu, không phân biệt dấu và hoa/thường; sắp theo tên. */
  list: (q?: string) =>
    http.get<PrescriptionTemplateSummary[]>(`/prescription-templates${qs({ q: q?.trim() })}`),
  get: (id: string) => http.get<PrescriptionTemplate>(`/prescription-templates/${id}`),
  create: (body: PrescriptionTemplateRequest) =>
    http.post<PrescriptionTemplate>("/prescription-templates", body),
  update: (id: string, body: PrescriptionTemplateRequest) =>
    http.put<PrescriptionTemplate>(`/prescription-templates/${id}`, body),
  /** Xoá hẳn. Không ảnh hưởng bệnh án đã lập vì bệnh án không trỏ vào mẫu. */
  remove: (id: string) => http.del<void>(`/prescription-templates/${id}`),
};
