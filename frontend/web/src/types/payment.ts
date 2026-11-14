/**
 * Thanh toán tiền mặt tại quầy cho đơn thuốc — không phải thanh toán trực tuyến.
 * Luồng: bác sĩ kê đơn → payment-service tạo phiếu → nhân viên nhập số tiền → thu tiền.
 */
export type PaymentStatus =
  | "PENDING_AMOUNT"
  | "PENDING_PAYMENT"
  | "COMPLETED"
  | "CANCELLED";

export type PaymentMethod = "CASH";

export interface PaymentItem {
  id: string;
  medicationName: string;
  dosage: string;
  frequency: string;
  durationDays: number | null;
  unitPrice: number | null;
  quantity: number | null;
  lineAmount: number | null;
}

export interface Payment {
  id: string;
  medicalRecordId: string;
  appointmentId: string;
  customerUserId: string;
  /** Tổng khám cần thu = tiền dịch vụ + tiền thuốc (chưa gồm sản phẩm mua thêm ở hoá đơn quầy). */
  amount: number | null;
  /** Dịch vụ khách đã đặt và tiền dịch vụ; null với phiếu tạo trước khi có tính năng này. */
  serviceName: string | null;
  serviceAmount: number | null;
  method: PaymentMethod;
  status: PaymentStatus;
  paidAt: string | null;
  items: PaymentItem[];
  createdAt: string;
  updatedAt: string;
}

/** Một dòng thuốc quầy chốt: đơn giá và số lượng thực tế phát ra. */
export interface SetItemsLine {
  itemId: string;
  unitPrice: number;
  quantity: number;
}

export interface SetItemsRequest {
  /** Rỗng được: lượt khám không kê thuốc thì phiếu chỉ có tiền dịch vụ. */
  items: SetItemsLine[];
  /** Tiền dịch vụ quầy xác nhận; bỏ trống = giữ số đang có trên phiếu. */
  serviceAmount?: number;
  serviceName?: string;
}

export interface SetAmountRequest {
  amount: number;
}
