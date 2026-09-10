import { http, qs } from "./client";
import type {
  Category,
  CategoryRequest,
  PageResponse,
  Product,
  ProductFilter,
  ProductRequest,
  StockAdjustmentRequest,
  StockMovement,
} from "@/types";

export const productApi = {
  /**
   * Công khai. Không truyền `activeOnly` từ giao diện: tham số này đang cho khách vãng lai
   * xem cả hàng đã ẩn (VD-01) và việc sửa thuộc về backend, không phải che ở frontend.
   */
  search: (filter: ProductFilter = {}) =>
    http.get<PageResponse<Product>>(
      `/products${qs({
        keyword: filter.keyword,
        categoryId: filter.categoryId,
        minPrice: filter.minPrice,
        maxPrice: filter.maxPrice,
        page: filter.page ?? 0,
        size: filter.size ?? 20,
      })}`,
      true,
    ),

  byId: (id: string) => http.get<Product>(`/products/${id}`, true),

  create: (body: ProductRequest) => http.post<Product>("/products", body),
  update: (id: string, body: ProductRequest) => http.put<Product>(`/products/${id}`, body),
  remove: (id: string) => http.del<void>(`/products/${id}`),

  /** STAFF/ADMIN. Mọi thay đổi tồn phải đi qua đây để có vết trong lịch sử. */
  adjustStock: (id: string, body: StockAdjustmentRequest) =>
    http.post<Product>(`/products/${id}/stock`, body),

  stockMovements: (id: string) => http.get<StockMovement[]>(`/products/${id}/stock-movements`),

  lowStock: () => http.get<Product[]>("/products/low-stock"),
};

export const categoryApi = {
  list: () => http.get<Category[]>("/categories", true),
  byId: (id: string) => http.get<Category>(`/categories/${id}`, true),
  create: (body: CategoryRequest) => http.post<Category>("/categories", body),
  update: (id: string, body: CategoryRequest) => http.put<Category>(`/categories/${id}`, body),
  remove: (id: string) => http.del<void>(`/categories/${id}`),
};
