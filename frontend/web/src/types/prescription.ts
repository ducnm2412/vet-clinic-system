/**
 * Danh mục thuốc và đơn mẫu của pet-service (/medications, /prescription-templates).
 * Danh mục chỉ để gợi ý khi kê đơn: đơn thuốc thật vẫn lưu tên thuốc dạng chữ
 * (PrescriptionItemRequest.medicationName), không trỏ vào danh mục.
 */

export interface Medication {
  id: string;
  name: string;
  /** Đơn vị tính (viên, gói, ml…). */
  unit: string | null;
  /** Lưu ý, chống chỉ định của thuốc — hiện cho bác sĩ lúc kê. */
  caution: string | null;
  /** false = đã ẩn khỏi gợi ý (không xoá cứng). */
  active: boolean;
  /** Giá bán một đơn vị (VND); null = chưa có giá, quầy nhập tay lúc thu tiền. */
  unitPrice: number | null;
}

export interface MedicationRequest {
  name: string;
  unit?: string;
  caution?: string;
  /** Để trống = chưa có giá. Khi sửa, để trống sẽ xoá giá cũ. */
  unitPrice?: number;
  /** Để trống khi sửa thì backend giữ nguyên trạng thái hiện tại. */
  active?: boolean;
}

/** Dòng trong danh sách đơn mẫu: đủ để nhận ra mẫu, chi tiết lấy riêng bằng GET /{id}. */
export interface PrescriptionTemplateSummary {
  id: string;
  name: string;
  diagnosis: string;
  itemCount: number;
}

/** Một thuốc trong đơn mẫu, kèm tên/đơn vị/lưu ý lấy từ danh mục tại thời điểm đọc. */
export interface PrescriptionTemplateLine {
  id: string;
  medicationId: string;
  medicationName: string;
  medicationUnit: string | null;
  medicationCaution: string | null;
  /** false = thuốc đã bị ẩn khỏi danh mục sau khi mẫu được tạo. */
  medicationActive: boolean;
  dosage: string;
  frequency: string;
  durationDays: number | null;
  notes: string | null;
}

export interface PrescriptionTemplate {
  id: string;
  name: string;
  diagnosis: string;
  treatment: string | null;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  items: PrescriptionTemplateLine[];
}

export interface PrescriptionTemplateLineRequest {
  medicationId: string;
  dosage: string;
  frequency: string;
  durationDays?: number;
  notes?: string;
}

export interface PrescriptionTemplateRequest {
  name: string;
  diagnosis: string;
  treatment?: string;
  notes?: string;
  /** Tối đa 30 thuốc; thứ tự trong mảng là thứ tự hiển thị. */
  items: PrescriptionTemplateLineRequest[];
}
