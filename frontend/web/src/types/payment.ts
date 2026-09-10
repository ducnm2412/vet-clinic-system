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
  amount: number | null;
  method: PaymentMethod;
  status: PaymentStatus;
  paidAt: string | null;
  items: PaymentItem[];
  createdAt: string;
  updatedAt: string;
}

export interface SetAmountRequest {
  amount: number;
}
