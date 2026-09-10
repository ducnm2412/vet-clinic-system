export interface Category {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryRequest {
  name: string;
  /** Chỉ chữ thường, số và dấu gạch ngang — backend chặn bằng regex. */
  slug: string;
  description?: string;
}

export interface Product {
  id: string;
  categoryId: string;
  categoryName: string;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  unit: string;
  imageUrl: string | null;
  stockQuantity: number;
  lowStockThreshold: number | null;
  lowStock: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  categoryId: string;
  sku: string;
  name: string;
  description?: string;
  price: number;
  unit: string;
  imageUrl?: string;
  /** Chỉ dùng khi tạo mới. Đổi tồn sau đó phải đi qua endpoint kho để có vết. */
  initialStock?: number;
  lowStockThreshold?: number;
  active?: boolean;
}

export type StockMovementType = "IMPORT" | "ADJUSTMENT" | "SALE" | "RETURN";

export interface StockMovement {
  id: string;
  productId: string;
  type: StockMovementType;
  quantityChange: number;
  quantityAfter: number;
  note: string | null;
  /** Với dòng SALE/RETURN đây là id đơn hàng sinh ra biến động. */
  referenceId: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface StockAdjustmentRequest {
  /** SALE và RETURN do RabbitMQ sinh ra, người dùng không nhập tay. */
  type: Extract<StockMovementType, "IMPORT" | "ADJUSTMENT">;
  /** Âm được khi kiểm kê thiếu (ADJUSTMENT), nhưng không bao giờ bằng 0. */
  quantityChange: number;
  note?: string;
}

export interface ProductFilter {
  keyword?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
  /**
   * Dạng "trường,chiều" — ví dụ "price,asc". Không phải tham số riêng của product-service
   * mà do Spring Pageable đọc, nên chỉ nhận đúng tên cột của entity: name, price, createdAt.
   */
  sort?: string;
}

/** Trạng thái tồn suy ra từ product, dùng chung cho badge và bộ lọc. */
export type StockStatus = "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK";

export function stockStatusOf(p: Product): StockStatus {
  if (p.stockQuantity <= 0) return "OUT_OF_STOCK";
  return p.lowStock ? "LOW_STOCK" : "IN_STOCK";
}
