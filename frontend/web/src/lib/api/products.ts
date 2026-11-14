import { http, qs } from "./client";
import { mockCategories, mockProductPage, mockProducts, withMock } from "./mock-data"; // MOCK-TẠM
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
    withMock(http.get<PageResponse<Product>>(
      `/products${qs({
        keyword: filter.keyword,
        categoryId: filter.categoryId,
        minPrice: filter.minPrice,
        maxPrice: filter.maxPrice,
        page: filter.page ?? 0,
        size: filter.size ?? 20,
        sort: filter.sort,
      })}`,
      true,
    ), () => mockProductPage(filter)), // MOCK-TẠM

  /**
   * Tìm sản phẩm cho quầy: CHỈ dữ liệu thật, không rơi về dữ liệu mẫu. Quầy gửi id sản phẩm lên hoá đơn và
   * trừ kho thật, nên hiện sản phẩm giả khi product-service tắt chỉ làm hoá đơn bị từ chối (id không phải
   * UUID) — thà báo lỗi ngay lúc tìm.
   */
  searchLive: (filter: ProductFilter = {}) =>
    http.get<PageResponse<Product>>(
      `/products${qs({ keyword: filter.keyword, page: filter.page ?? 0, size: filter.size ?? 20 })}`,
      true,
    ),

  /** Như `searchLive`: lấy lại một sản phẩm thật, lỗi thì ném (không trả sản phẩm mẫu đầu danh sách). */
  byIdLive: (id: string) => http.get<Product>(`/products/${id}`, true),

  byId: (id: string) => withMock(http.get<Product>(`/products/${id}`, true), () => mockProducts.find((x) => x.id === id) ?? mockProducts[0]), // MOCK-TẠM

  create: (body: ProductRequest) => http.post<Product>("/products", body),
  update: (id: string, body: ProductRequest) => http.put<Product>(`/products/${id}`, body),
  /**
   * VD-03: không có xoá sản phẩm. Xoá sẽ cuốn theo toàn bộ lịch sử nhập xuất kho của mặt hàng,
   * trong khi đơn hàng cũ vẫn đang trỏ tới nó — nên chỉ ẩn khỏi cửa hàng, giống khoá tài khoản.
   */
  hide: (id: string) => http.put<Product>(`/products/${id}/hide`),
  unhide: (id: string) => http.put<Product>(`/products/${id}/unhide`),

  /** STAFF/ADMIN. Mọi thay đổi tồn phải đi qua đây để có vết trong lịch sử. */
  adjustStock: (id: string, body: StockAdjustmentRequest) =>
    http.post<Product>(`/products/${id}/stock`, body),

  stockMovements: (id: string) => http.get<StockMovement[]>(`/products/${id}/stock-movements`),

  lowStock: () => http.get<Product[]>("/products/low-stock"),
};

export const categoryApi = {
  list: () => withMock(http.get<Category[]>("/categories", true), () => mockCategories), // MOCK-TẠM
  byId: (id: string) => http.get<Category>(`/categories/${id}`, true),
  create: (body: CategoryRequest) => http.post<Category>("/categories", body),
  update: (id: string, body: CategoryRequest) => http.put<Category>(`/categories/${id}`, body),
  remove: (id: string) => http.del<void>(`/categories/${id}`),
};
