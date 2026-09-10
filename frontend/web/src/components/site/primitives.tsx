import Link from "next/link";
import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

/*
  Bộ phận tử nền của website khách hàng. Cố tình tách khỏi components/ui — bộ kia phục vụ
  dashboard (nút vuông 6px, dày đặc thông tin), bộ này phục vụ mặt tiền (nút viên thuốc,
  nhiều khoảng thở). Trộn chung thì một bên luôn phải chịu thiệt.
*/

const BUTTON_VARIANT = {
  /* San hô đậm. Mỗi màn hình chỉ nên có một nút mang màu này. */
  primary: "bg-coral-deep text-white hover:bg-[#b3361f] disabled:bg-coral-deep/45",
  teal: "bg-teal text-white hover:bg-teal-deep disabled:bg-teal/45",
  outline: "border border-mist bg-white text-pine hover:border-teal hover:bg-mint",
  /* Trên nền tối. */
  light: "bg-white text-pine hover:bg-mint",
} as const;

const BUTTON_SIZE = {
  sm: "h-10 px-4 text-[15px]",
  md: "h-12 px-6 text-[16px]",
  lg: "h-14 px-8 text-[17px]",
} as const;

const buttonBase =
  "inline-flex shrink-0 items-center justify-center gap-2 rounded-full font-medium " +
  "transition-[background-color,border-color,transform] duration-200 [transition-timing-function:var(--ease)] " +
  "active:translate-y-px disabled:cursor-not-allowed disabled:active:translate-y-0";

export interface SiteButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: keyof typeof BUTTON_VARIANT;
  size?: keyof typeof BUTTON_SIZE;
  loading?: boolean;
}

export function SiteButton({
  variant = "primary",
  size = "md",
  loading,
  disabled,
  className,
  children,
  ...props
}: SiteButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(buttonBase, BUTTON_VARIANT[variant], BUTTON_SIZE[size], className)}
      {...props}
    >
      {loading && (
        <span
          aria-hidden
          className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}

export function ButtonLink({
  href,
  variant = "primary",
  size = "md",
  className,
  children,
  ...props
}: {
  href: string;
  variant?: keyof typeof BUTTON_VARIANT;
  size?: keyof typeof BUTTON_SIZE;
  children: ReactNode;
} & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "href">) {
  return (
    <Link
      href={href}
      className={cn(buttonBase, BUTTON_VARIANT[variant], BUTTON_SIZE[size], className)}
      {...props}
    >
      {children}
    </Link>
  );
}

/**
 * Một dải nội dung. `tone` quyết định nền, `flush` bỏ khoảng đệm dưới khi dải sau cần
 * dính liền vào (dùng cho chỗ mái vòm tràn qua ranh giới).
 */
export function Section({
  tone = "white",
  id,
  className,
  children,
}: {
  tone?: "white" | "mint" | "peach" | "pine";
  id?: string;
  className?: string;
  children: ReactNode;
}) {
  const TONE = {
    white: "bg-white text-pine",
    mint: "bg-mint text-pine",
    peach: "bg-peach text-pine",
    pine: "bg-pine text-white",
  } as const;

  return (
    <section id={id} className={cn("py-20 md:py-28", TONE[tone], className)}>
      <div className="mx-auto w-full max-w-[1200px] px-5 md:px-6">{children}</div>
    </section>
  );
}

/** Khung trong, dùng cho những chỗ không phải là dải nền màu. */
export function Container({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("mx-auto w-full max-w-[1200px] px-5 md:px-6", className)}>{children}</div>
  );
}

/**
 * Tiêu đề của một dải. Không có nhãn IN HOA phía trên — đó là thứ trang nào cũng có và
 * nó chẳng nói thêm điều gì. Chỗ nào cần dẫn dắt thì viết hẳn một câu.
 */
export function SectionHead({
  title,
  lede,
  align = "left",
  tone = "dark",
  action,
}: {
  title: string;
  lede?: string;
  align?: "left" | "center";
  tone?: "dark" | "light";
  action?: ReactNode;
}) {
  return (
    <div
      className={cn(
        "mb-12 flex flex-wrap items-end gap-x-8 gap-y-4",
        align === "center" && "flex-col items-center text-center",
      )}
    >
      <div className={cn("min-w-0 flex-1", align === "center" && "flex-none")}>
        <h2 className="t-h2">{title}</h2>
        {lede && (
          <p
            className={cn(
              "measure t-lede mt-4",
              tone === "light" ? "text-white/75" : "text-stone",
              align === "center" && "mx-auto",
            )}
          >
            {lede}
          </p>
        )}
      </div>
      {action}
    </div>
  );
}

/**
 * Dấu chân — lấy từ logo của dự án (assets/logo/vet_clinic_icon.svg). Dùng làm hình thay
 * thế mỗi khi không có ảnh thật, thay vì để khung rỗng hoặc ảnh vỡ.
 */
export function PawMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 200 200" aria-hidden className={className} fill="currentColor">
      <ellipse cx="100" cy="117" rx="40" ry="35" />
      <circle cx="57" cy="73" r="16" />
      <circle cx="81" cy="51" r="17" />
      <circle cx="119" cy="51" r="17" />
      <circle cx="143" cy="73" r="16" />
    </svg>
  );
}

/**
 * Mái vòm rỗng: nền màu phẳng kèm dấu chân mờ. Đây là chỗ đứng của mọi ảnh chưa có —
 * sản phẩm thiếu `imageUrl`, thú cưng chưa có ảnh đại diện.
 */
export function ArchPlaceholder({
  tone = 0,
  className,
  small,
}: {
  /** Số bất kỳ; dùng để chọn màu nền sao cho cùng một thứ luôn ra cùng một màu. */
  tone?: number;
  className?: string;
  small?: boolean;
}) {
  /*
    Ba cặp màu, mỗi cặp là một đĩa tròn đậm hơn nền một chút với dấu chân nổi lên trên.
    Bản đầu chỉ có dấu chân mờ trên nền phẳng, nhìn như ô trống chưa tải xong — có đĩa
    tròn thì mắt đọc ra ngay đây là một hình cố ý.
  */
  const TONES = [
    { skin: "bg-mint", disc: "bg-teal/12", paw: "text-teal/55" },
    { skin: "bg-peach", disc: "bg-coral/12", paw: "text-coral/60" },
    { skin: "bg-mist", disc: "bg-pine/8", paw: "text-pine/45" },
  ];
  const t = TONES[Math.abs(tone) % TONES.length];

  return (
    <div
      className={cn(
        small ? "arch-sm" : "arch",
        "relative flex items-center justify-center",
        t.skin,
        className,
      )}
    >
      <span
        aria-hidden
        className={cn("absolute aspect-square w-[58%] rounded-full", t.disc)}
      />
      <PawMark className={cn("relative w-[30%] max-w-28", t.paw)} />
    </div>
  );
}
