"use client";

import { TemplateManager } from "@/components/medical/TemplateManager";

/**
 * Đơn mẫu là nội dung chuyên môn nên chỉ bác sĩ có trang quản lý trong menu. Backend vẫn cho cả
 * ADMIN gọi API; khi cần chỉ thêm một trang mỏng như thế này cho ADMIN.
 */
export default function DoctorTemplatesPage() {
  return <TemplateManager />;
}
