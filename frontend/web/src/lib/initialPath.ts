/**
 * Đường dẫn (kèm query) của lần tải trang đầu tiên trong tab — tức là lúc người dùng gõ địa chỉ,
 * mở lại tab, bấm bookmark hoặc F5. Chuyển trang bên trong web (Link, router.push) không đổi giá
 * trị này. Module được Providers import nên chạy ngay khi app khởi động, trước mọi lần chuyển trang.
 */
const initialPath = typeof window === "undefined" ? "" : window.location.pathname + window.location.search;

let consumed = false;

/**
 * true đúng MỘT lần nếu lần tải trang đầu tiên là `path`. Các lần gọi sau (kể cả khi người dùng
 * bấm "Trang chủ" để quay về) đều trả false.
 */
export function consumeInitialLoadAt(path: string): boolean {
  if (consumed) return false;
  consumed = true;
  return initialPath === path;
}
