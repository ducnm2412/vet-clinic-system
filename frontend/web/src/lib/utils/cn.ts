/** Ghép class, bỏ giá trị rỗng. Đủ dùng cho dự án này — không cần thêm clsx/tailwind-merge. */
export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(" ");
}
