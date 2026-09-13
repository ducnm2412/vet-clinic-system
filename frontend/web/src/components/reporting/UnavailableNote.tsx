import { AlertTriangle } from "lucide-react";
import type { ReportSource } from "@/types";
import { SOURCE_LABEL } from "./format";

/**
 * Nói rõ phần nào đang thiếu. Không có dòng này, admin sẽ đọc "—" hay cột trống thành
 * "hôm nay không bán được gì".
 */
export function UnavailableNote({ sources }: { sources: ReportSource[] }) {
  if (sources.length === 0) return null;
  const list = sources.map((s) => SOURCE_LABEL[s]).join(", ");
  return (
    <p
      role="status"
      className="flex items-start gap-2 rounded-[var(--radius-control)] border border-amber/30 bg-amber-wash px-3 py-2 text-sm text-ink"
    >
      <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0 text-amber" />
      <span>
        Chưa lấy được {list} — dịch vụ tương ứng đang không phản hồi. Các số đó hiện “—”, không phải
        bằng 0. Thử tải lại sau ít phút.
      </span>
    </p>
  );
}
