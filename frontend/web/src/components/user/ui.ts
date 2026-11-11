/**
 * Design token của khu vực khách hàng (/user). Màu thương hiệu giữ theo website (cam #f57c1f,
 * xanh #1f4a3c); chữ, viền, nền theo hệ trung tính. Dùng các hằng này thay vì gõ lại mã màu.
 */
export const C = {
  orange: "#f57c1f",
  orangeHover: "#e46d12",
  orangeSoft: "#fff1e3",
  green: "#1f4a3c",
  greenSoft: "#e3f2ec",
  cream: "#fffcf8",
  text: "#242424",
  muted: "#777777",
  border: "#eeeeee",
} as const;

/** Thẻ trắng chuẩn: bo 20px, viền nhạt, bóng rất nhẹ. */
export const card = "rounded-[20px] border border-[#eeeeee] bg-white shadow-[0_6px_24px_-18px_rgba(36,36,36,0.25)]";
/** Hiệu ứng nâng 4px khi rê chuột. */
export const cardHover =
  "transition duration-200 hover:-translate-y-1 hover:border-[#f9cfa8] hover:shadow-[0_16px_32px_-22px_rgba(245,124,31,0.55)]";

export const btnPrimary =
  "inline-flex h-11 items-center justify-center gap-2 rounded-full bg-[#f57c1f] px-5 text-sm font-semibold text-white shadow-[0_8px_18px_-10px_rgba(245,124,31,0.9)] transition-colors hover:bg-[#e46d12] disabled:opacity-60";
export const btnOutline =
  "inline-flex h-10 items-center justify-center gap-1.5 rounded-full border border-[#eeeeee] bg-white px-4 text-sm font-medium text-[#242424] transition-colors hover:border-[#f57c1f] hover:text-[#f57c1f] disabled:opacity-50";
export const btnOrangeOutline =
  "inline-flex h-9 shrink-0 items-center justify-center gap-1 rounded-full border border-[#f57c1f] px-4 text-sm font-semibold text-[#f57c1f] transition-colors hover:bg-[#f57c1f] hover:text-white";

export const field =
  "h-12 w-full rounded-xl border border-[#eeeeee] bg-white px-4 text-[15px] text-[#242424] outline-none transition placeholder:text-[#aaaaaa] focus:border-[#f57c1f] focus:ring-2 focus:ring-[#f57c1f]/15 disabled:bg-[#fafafa] disabled:text-[#777777]";
export const fieldLabel = "mb-1.5 block text-sm font-medium text-[#242424]";
