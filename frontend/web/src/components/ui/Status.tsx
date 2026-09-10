import { cn } from "@/lib/utils/cn";
import type { StatusStyle } from "@/lib/utils/status";

/**
 * Nhãn trạng thái: chấm màu cộng chữ. Chấm mang thông tin thừa có chủ đích — nếu người
 * xem không phân biệt được màu thì chữ vẫn nói đủ.
 */
export function StatusTag({ status, className }: { status: StatusStyle; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-[var(--radius-badge)] px-2 py-0.5 text-[13px] font-medium",
        status.tint,
        className,
      )}
    >
      <span aria-hidden className={cn("size-1.5 shrink-0 rounded-full", status.dot)} />
      {status.label}
    </span>
  );
}

/** Nhãn trung tính cho những thứ không phải trạng thái: vai trò, danh mục, SKU. */
export function Tag({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-[var(--radius-badge)] border border-line px-2 py-0.5 text-[13px] text-ink-soft",
        className,
      )}
    >
      {children}
    </span>
  );
}
