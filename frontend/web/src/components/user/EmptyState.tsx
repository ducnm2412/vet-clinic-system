import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-[20px] border border-dashed border-[#f3d3b3] bg-[#fffcf8] px-6 py-14 text-center">
      <span aria-hidden className="mx-auto grid size-20 place-items-center rounded-full bg-[#fff1e3] text-[#f57c1f]">
        <Icon className="size-9" strokeWidth={1.6} />
      </span>
      <p className="mt-5 text-lg font-bold text-[#1f4a3c]">{title}</p>
      {description && <p className="mx-auto mt-2 max-w-sm text-[15px] text-[#777777]">{description}</p>}
      {action && <div className="mt-6 flex justify-center">{action}</div>}
    </div>
  );
}

/** Thông báo cho tính năng chưa có backend (hiện trên production thay cho dữ liệu mẫu). */
export function ComingSoon({ icon, title }: { icon: LucideIcon; title: string }) {
  return (
    <EmptyState
      icon={icon}
      title={title}
      description="Tính năng đang được phát triển và sẽ sớm ra mắt. Cần hỗ trợ ngay, bạn vui lòng gọi hotline phòng khám."
    />
  );
}

export function ErrorState({ title, onRetry }: { title: string; onRetry: () => void }) {
  return (
    <div className="rounded-[20px] border border-[#eeeeee] px-6 py-14 text-center">
      <p className="text-lg font-bold text-[#1f4a3c]">{title}</p>
      <p className="mt-2 text-[15px] text-[#777777]">Kết nối tới phòng khám đang trục trặc.</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-6 inline-flex h-10 items-center rounded-full border border-[#eeeeee] px-5 text-sm font-medium hover:border-[#f57c1f] hover:text-[#f57c1f]"
      >
        Thử lại
      </button>
    </div>
  );
}

export function SkeletonList({ count = 3, className = "h-24" }: { count?: number; className?: string }) {
  return (
    <ul className="space-y-3" aria-hidden>
      {Array.from({ length: count }, (_, i) => (
        <li key={i} className={`animate-pulse rounded-[20px] bg-[#fff5ec] ${className}`} />
      ))}
    </ul>
  );
}
