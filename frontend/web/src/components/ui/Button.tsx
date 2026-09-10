"use client";

import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

const VARIANT = {
  primary: "bg-moss text-white hover:bg-moss-hover disabled:bg-moss/40",
  secondary: "border border-line-strong bg-surface text-ink hover:bg-paper disabled:opacity-50",
  ghost: "text-ink-soft hover:bg-paper hover:text-ink disabled:opacity-50",
  danger: "border border-danger/40 bg-surface text-danger hover:bg-danger-wash disabled:opacity-50",
} as const;

const SIZE = {
  sm: "h-8 gap-1.5 px-2.5 text-[13px]",
  md: "h-9 gap-2 px-3.5 text-sm",
  lg: "h-11 gap-2 px-5 text-[15px]",
} as const;

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: keyof typeof VARIANT;
  size?: keyof typeof SIZE;
  /** Khoá nút và hiện vòng quay — dùng khi đang gửi biểu mẫu. */
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", size = "md", loading, disabled, className, children, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "inline-flex items-center justify-center rounded-[var(--radius-control)] font-medium",
        "transition-colors duration-[120ms] [transition-timing-function:var(--ease)]",
        "disabled:cursor-not-allowed",
        VARIANT[variant],
        SIZE[size],
        className,
      )}
      {...props}
    >
      {loading && (
        <span
          aria-hidden
          className="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
});

/**
 * Nút chỉ có biểu tượng. Bắt buộc truyền `label` — nó thành aria-label và tooltip,
 * nếu không người dùng bàn phím và trình đọc màn hình sẽ không biết nút làm gì.
 */
export const IconButton = forwardRef<
  HTMLButtonElement,
  Omit<ButtonProps, "children"> & { label: string; children: React.ReactNode }
>(function IconButton({ label, size = "md", className, children, ...props }, ref) {
  return (
    <Button
      ref={ref}
      aria-label={label}
      title={label}
      size={size}
      className={cn(size === "sm" ? "w-8 !px-0" : "w-9 !px-0", className)}
      {...props}
    >
      {children}
    </Button>
  );
});
