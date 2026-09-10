import { http } from "./client";
import type { Payment, SetAmountRequest } from "@/types";

/**
 * Thu tiền mặt tại quầy cho đơn thuốc. Toàn bộ nhánh này chỉ STAFF/ADMIN dùng được —
 * khách hàng không có endpoint nào để tự xem phiếu thu của mình (VD-18).
 */
export const paymentApi = {
  /** Phiếu chưa thu xong: gồm cả loại chưa nhập số tiền và loại chờ khách trả. */
  pending: () => http.get<Payment[]>("/payment/payments/pending"),

  /** Nhân viên nhập số tiền sau khi tính theo đơn thuốc. */
  setAmount: (id: string, body: SetAmountRequest) =>
    http.put<Payment>(`/payment/payments/${id}/amount`, body),

  /** Xác nhận đã nhận tiền mặt — phát `payment.completed` để booking mở đơn thuốc. */
  confirmCash: (id: string) => http.put<Payment>(`/payment/payments/${id}/confirm-cash`),
};
