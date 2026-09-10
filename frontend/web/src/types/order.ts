export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "SHIPPING"
  | "COMPLETED"
  | "CANCELLED";

/** order-service hiện chỉ hỗ trợ COD — không có thanh toán trực tuyến. */
export type OrderPaymentMethod = "COD";

export interface CartItem {
  productId: string;
  sku: string;
  name: string;
  unit: string;
  price: number;
  quantity: number;
  lineTotal: number;
  /** Giá và tồn hỏi product-service mỗi lần tải giỏ, không tin số liệu client giữ. */
  available: boolean;
  unavailableReason: string | null;
}

export interface Cart {
  items: CartItem[];
  totalItems: number;
  subtotal: number;
  /** false khi có ít nhất một dòng không mua được — backend sẽ từ chối checkout. */
  checkoutable: boolean;
}

export interface OrderItem {
  productId: string;
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface OrderSummary {
  id: string;
  orderCode: string;
  status: OrderStatus;
  totalItems: number;
  total: number;
  recipientName: string;
  createdAt: string;
}

export interface Order {
  id: string;
  orderCode: string;
  userId: string;
  status: OrderStatus;
  paymentMethod: OrderPaymentMethod;
  recipientName: string;
  recipientPhone: string;
  shippingAddress: string;
  note: string | null;
  subtotal: number;
  shippingFee: number;
  total: number;
  items: OrderItem[];
  createdAt: string;
  confirmedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
}

export interface OrderStatusHistory {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  changedBy: string | null;
  note: string | null;
  createdAt: string;
}

export interface CheckoutRequest {
  recipientName: string;
  /** 10 chữ số bắt đầu bằng 0 — backend chặn bằng regex. */
  recipientPhone: string;
  shippingAddress: string;
  note?: string;
  paymentMethod: OrderPaymentMethod;
}

export interface AddToCartRequest {
  productId: string;
  quantity: number;
}
