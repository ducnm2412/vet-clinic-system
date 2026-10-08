export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "SHIPPING"
  | "COMPLETED"
  | "CANCELLED";

/**
 * COD và ONLINE là hai cách khách trả khi đặt qua giỏ hàng. CASH và BANK_TRANSFER do nhân viên
 * chọn khi thu tại quầy.
 */
export type OrderPaymentMethod = "COD" | "ONLINE" | "CASH" | "BANK_TRANSFER";

/** Kênh bán: giỏ hàng giao tận nơi, hay nhân viên lập và thu ngay tại quầy. */
export type OrderChannel = "ONLINE" | "COUNTER";

export type OrderPaymentStatus = "UNPAID" | "PAID";

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
  channel: OrderChannel;
  paymentMethod: OrderPaymentMethod | null;
  paymentStatus: OrderPaymentStatus;
  totalItems: number;
  total: number;
  recipientName: string;
  createdAt: string;
  /** Đơn online đã trả tiền nhưng bị huỷ: nhân viên cần hoàn tiền thủ công. */
  refundRequired: boolean;
}

export interface Order {
  id: string;
  orderCode: string;
  /** null với hoá đơn tại quầy của khách lẻ không có tài khoản. */
  userId: string | null;
  status: OrderStatus;
  channel: OrderChannel;
  /** null khi hoá đơn chưa có phương thức thu (không xảy ra với hoá đơn tại quầy). */
  paymentMethod: OrderPaymentMethod | null;
  paymentStatus: OrderPaymentStatus;
  recipientName: string;
  recipientPhone: string;
  shippingAddress: string;
  note: string | null;
  subtotal: number;
  shippingFee: number;
  /** Khoản khám/thuốc gộp vào hoá đơn (payment-service). 0 khi không có. */
  examPaymentId: string | null;
  examAmount: number;
  total: number;
  items: OrderItem[];
  createdAt: string;
  confirmedAt: string | null;
  completedAt: string | null;
  paidAt: string | null;
  /** Hạn trả của đơn online chưa thanh toán; quá hạn đơn tự huỷ. null với đơn khác. */
  paymentExpiresAt: string | null;
  /** Mã giao dịch ngân hàng nhân viên ghi khi thu chuyển khoản. */
  transferReference: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  refundRequired: boolean;
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
  /** Đặt qua giỏ hàng chỉ có COD hoặc ONLINE — backend từ chối các cách thu tại quầy. */
  paymentMethod: Extract<OrderPaymentMethod, "COD" | "ONLINE">;
}

/** Link sang cổng để trả tiền cho đơn online. paymentUrl có thể là đường dẫn trong web hoặc địa chỉ ngoài. */
export interface PaymentInit {
  orderId: string;
  paymentUrl: string;
  expiresAt: string | null;
}

export type PaymentOutcome =
  | "PAID"
  | "ALREADY_PAID"
  | "FAILED"
  | "UNKNOWN_TRANSACTION"
  | "AMOUNT_MISMATCH";

export interface PaymentResult {
  outcome: PaymentOutcome;
  /** null khi không tìm thấy giao dịch. */
  orderId: string | null;
  orderCode: string | null;
  paymentStatus: OrderPaymentStatus | null;
}

/** Lựa chọn của khách trên trang cổng giả lập, kèm đúng các tham số (và chữ ký) của link thanh toán. */
export interface MockPaymentSubmit {
  txnRef: string;
  orderCode: string;
  amount: string;
  signature: string;
  outcome: "SUCCESS" | "FAILED" | "CANCELLED";
}

/** Dòng sản phẩm khách mua thêm tại quầy. Giá luôn do server lấy, không gửi lên. */
export interface CounterInvoiceLine {
  productId: string;
  quantity: number;
}

/**
 * Lập và thu hoá đơn tại quầy trong một lần. Tiền khám và chủ khoản khám do server đọc từ
 * payment-service theo examPaymentId; hai trường customerName/customerPhone chỉ để in.
 */
export interface CounterInvoiceRequest {
  examPaymentId?: string;
  customerName?: string;
  /** 10 chữ số bắt đầu bằng 0, hoặc bỏ trống. */
  customerPhone?: string;
  items?: CounterInvoiceLine[];
  paymentMethod: Extract<OrderPaymentMethod, "CASH" | "BANK_TRANSFER">;
  /** Chỉ có nghĩa với chuyển khoản. */
  transferReference?: string;
  note?: string;
}

export interface AddToCartRequest {
  productId: string;
  quantity: number;
}
