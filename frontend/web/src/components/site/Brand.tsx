import Link from "next/link";
import { cn } from "@/lib/utils/cn";

/**
 * Dấu hiệu nhận diện, vẽ lại đúng theo assets/logo/vet_clinic_icon.svg: dấu chân trắng
 * trên đĩa mòng két, chữ thập y tế san hô ở góc. Để dạng SVG nội tuyến thay vì tệp ảnh
 * để nó ăn theo màu chữ khi đặt trên nền tối.
 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 200 200" role="img" aria-label="Vet Clinic" className={className}>
      <circle cx="100" cy="100" r="96" fill="var(--teal)" />
      <ellipse cx="100" cy="117" rx="40" ry="35" fill="#fff" />
      <circle cx="57" cy="73" r="16" fill="#fff" />
      <circle cx="81" cy="51" r="17" fill="#fff" />
      <circle cx="119" cy="51" r="17" fill="#fff" />
      <circle cx="143" cy="73" r="16" fill="#fff" />
      <circle cx="149" cy="149" r="32" fill="var(--coral)" stroke="#fff" strokeWidth="7" />
      <rect x="140" y="133" width="19" height="32" rx="3.5" fill="#fff" />
      <rect x="133" y="140" width="32" height="19" rx="3.5" fill="#fff" />
    </svg>
  );
}

export function BrandLockup({
  tone = "dark",
  className,
}: {
  tone?: "dark" | "light";
  className?: string;
}) {
  return (
    <Link
      href="/"
      className={cn("inline-flex shrink-0 items-center gap-2.5 whitespace-nowrap", className)}
      aria-label="Vet Clinic, về trang chủ"
    >
      <BrandMark className="size-9 shrink-0" />
      {/*
        Dưới 640px chỉ còn dấu hiệu, giấu chữ đi — chỗ đó phải nhường cho nút đặt lịch,
        giỏ hàng và nút menu, ba thứ người ta thật sự bấm.
      */}
      <span
        className={cn(
          "hidden font-[family-name:var(--font-brand)] text-[19px] font-semibold tracking-tight sm:inline",
          tone === "light" ? "text-white" : "text-pine",
        )}
      >
        Vet Clinic
      </span>
    </Link>
  );
}
