/** Kiểu phân trang chung của backend (product-service, order-service). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Thân lỗi chuẩn của GlobalExceptionHandler ở mọi service. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}
