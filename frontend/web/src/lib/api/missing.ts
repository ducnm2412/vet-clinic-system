/**
 * Ba mảng giao diện chưa có API backend (xem docs/van-de-ton-dong.md — VD-18).
 *
 * Tầng này tồn tại để component không phải chứa dữ liệu giả. Mỗi mục khai báo rõ endpoint
 * còn thiếu; màn hình dùng nó sẽ hiển thị khối "chưa có dữ liệu" kèm ghi chú, chứ KHÔNG
 * dựng số liệu bịa rồi trình bày như thật.
 *
 * Khi backend bổ sung endpoint: thay hàm tương ứng bằng lời gọi thật trong lib/api/*.ts
 * rồi xoá mục khỏi đây. Không sửa gì trong component.
 */

export interface MissingEndpoint {
  /** Nhãn hiển thị cho người dùng. */
  label: string;
  /** Endpoint đề xuất, để dán thẳng vào issue backend. */
  suggested: string;
  /** Vì sao chưa có. */
  reason: string;
}

export const MISSING: Record<string, MissingEndpoint> = {
  attendance: {
    label: "Chấm công",
    suggested: "GET /staff/attendance?userId=&month=",
    reason: "staff-service chưa được triển khai.",
  },
  petMedicalHistory: {
    label: "Lịch sử khám của thú cưng",
    suggested: "GET /booking/pets/{petId}/medical-records",
    reason: "Bệnh án hiện chỉ tra được theo từng lịch hẹn (VD-17).",
  },
  notifications: {
    label: "Thông báo trong ứng dụng",
    suggested: "GET /notifications?unreadOnly=&page=&size=",
    reason: "notification-service thuần tiêu thụ sự kiện, không có REST endpoint.",
  },
};

export type MissingKey = keyof typeof MISSING;
