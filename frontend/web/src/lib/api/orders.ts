import { http, qs } from "./client";
import type {
  AddToCartRequest,
  Cart,
  CheckoutRequest,
  CounterInvoiceRequest,
  Order,
  OrderStatus,
  OrderStatusHistory,
  MockPaymentSubmit,
  OrderSummary,
  PageResponse,
  PaymentInit,
  PaymentResult,
} from "@/types";

/** Giỏ hàng chỉ dành cho CUSTOMER — backend chặn các role khác ở /cart/**. */
export const cartApi = {
  get: () => http.get<Cart>("/cart"),
  addItem: (body: AddToCartRequest) => http.post<Cart>("/cart/items", body),
  updateItem: (productId: string, quantity: number) =>
    http.put<Cart>(`/cart/items/${productId}`, { quantity }),
  removeItem: (productId: string) => http.del<Cart>(`/cart/items/${productId}`),
  clear: () => http.del<void>("/cart"),
};

export const orderApi = {
  checkout: (body: CheckoutRequest) => http.post<Order>("/orders", body),

  /** Đơn của chính mình. */
  mine: (page = 0, size = 20) =>
    http.get<PageResponse<OrderSummary>>(`/orders${qs({ page, size })}`),
  byId: (id: string) => http.get<Order>(`/orders/${id}`),
  history: (id: string) => http.get<OrderStatusHistory[]>(`/orders/${id}/history`),
  cancelMine: (id: string, reason: string) => http.post<Order>(`/orders/${id}/cancel`, { reason }),

  /** Lấy link sang cổng để trả tiền đơn online. Mỗi lần gọi cấp link mới và vô hiệu link cũ. */
  startPayment: (id: string) => http.post<PaymentInit>(`/orders/${id}/pay`),
  /** Chỉ dùng ở trang cổng giả lập (chạy thử khi chưa có cổng thật). */
  submitMockPayment: (body: MockPaymentSubmit) =>
    http.post<PaymentResult>("/orders/pay/mock/submit", body),
  /**
   * Trang trả về của VNPAY gửi nguyên các tham số vnp_* (kèm chữ ký) lên đây. Không cần đăng nhập: backend
   * tin chữ ký, không tin người gọi. Gọi lặp lại không gây thêm tác dụng.
   */
  vnpayReturn: (params: Record<string, string>) =>
    http.get<PaymentResult>(`/orders/pay/vnpay/return${qs(params)}`),
};

/** Nhánh quản trị đơn — STAFF/ADMIN. Đặt ở /orders/manage vì gateway đã dành /admin cho auth. */
export const orderManageApi = {
  list: (params: { status?: OrderStatus; page?: number; size?: number } = {}) =>
    http.get<PageResponse<OrderSummary>>(
      `/orders/manage${qs({ status: params.status, page: params.page ?? 0, size: params.size ?? 20 })}`,
    ),
  byId: (id: string) => http.get<Order>(`/orders/manage/${id}`),

  /**
   * Hoá đơn gộp tại quầy (tiền khám + sản phẩm), lập và thu trong một lần. Trừ kho ngay và,
   * nếu có khoản khám, hoàn tất khoản đó ở payment-service qua `order.invoice-paid`.
   */
  createCounterInvoice: (body: CounterInvoiceRequest) =>
    http.post<Order>("/orders/manage/counter", body),

  /**
   * Xác nhận là bước phát `order.completed` để product-service trừ kho.
   * Huỷ đơn đã xác nhận phát `order.cancelled` để hoàn kho.
   */
  confirm: (id: string) => http.post<Order>(`/orders/manage/${id}/confirm`),
  ship: (id: string) => http.post<Order>(`/orders/manage/${id}/ship`),
  complete: (id: string) => http.post<Order>(`/orders/manage/${id}/complete`),
  cancel: (id: string, reason: string) =>
    http.post<Order>(`/orders/manage/${id}/cancel`, { reason }),
};

/** Bước hợp lệ tiếp theo. Luật thật nằm ở backend, đây chỉ để biết nút nào nên hiện. */
export function nextActions(status: OrderStatus): Array<"confirm" | "ship" | "complete" | "cancel"> {
  switch (status) {
    case "PENDING":
      return ["confirm", "cancel"];
    case "CONFIRMED":
      return ["ship", "cancel"];
    case "SHIPPING":
      return ["complete", "cancel"];
    default:
      return [];
  }
}
