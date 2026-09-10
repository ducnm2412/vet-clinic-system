import { http, qs } from "./client";
import type {
  AddToCartRequest,
  Cart,
  CheckoutRequest,
  Order,
  OrderStatus,
  OrderStatusHistory,
  OrderSummary,
  PageResponse,
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
};

/** Nhánh quản trị đơn — STAFF/ADMIN. Đặt ở /orders/manage vì gateway đã dành /admin cho auth. */
export const orderManageApi = {
  list: (params: { status?: OrderStatus; page?: number; size?: number } = {}) =>
    http.get<PageResponse<OrderSummary>>(
      `/orders/manage${qs({ status: params.status, page: params.page ?? 0, size: params.size ?? 20 })}`,
    ),
  byId: (id: string) => http.get<Order>(`/orders/manage/${id}`),

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
