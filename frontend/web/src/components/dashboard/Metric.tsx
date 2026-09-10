import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { Skeleton } from "@/components/ui";

/**
 * Ô số liệu. Không có phần "so với kỳ trước" vì backend chưa có API thống kê nào trả về
 * số liệu lịch sử (VD-18) — thà bỏ trống còn hơn hiện mũi tên tăng giảm không có thật.
 */
export function Metric({
  label,
  value,
  unit,
  icon: Icon,
  loading,
  tone = "plain",
}: {
  label: string;
  value: number | string | null;
  unit?: string;
  icon: LucideIcon;
  loading?: boolean;
  /** "alert" dùng khi con số này là thứ cần xử lý ngay, ví dụ hàng sắp hết. */
  tone?: "plain" | "alert";
}) {
  return (
    <div className="flex items-start gap-3 rounded-[var(--radius-control)] border border-line bg-surface px-4 py-3.5">
      <Icon
        aria-hidden
        className={cn("mt-0.5 size-4 shrink-0", tone === "alert" ? "text-amber" : "text-bark")}
      />
      <div className="min-w-0">
        <p className="text-sm text-bark">{label}</p>
        {loading ? (
          <Skeleton className="mt-1.5 h-7 w-16" />
        ) : (
          <p className="mt-0.5 font-[family-name:var(--font-display)] text-[25px] leading-tight text-ink tnum">
            {value ?? "—"}
            {unit && <span className="ml-1 text-sm font-normal text-bark">{unit}</span>}
          </p>
        )}
      </div>
    </div>
  );
}

export function MetricRow({ children }: { children: React.ReactNode }) {
  return <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{children}</div>;
}
