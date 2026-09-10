"use client";

import type { ReactNode } from "react";
import { AlertTriangle, Inbox, Wrench } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { Button } from "./Button";
import type { MissingEndpoint } from "@/lib/api";

export function Spinner({ label = "Đang tải" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-2.5 py-8 text-sm text-bark">
      <span
        aria-hidden
        className="size-4 animate-spin rounded-full border-2 border-moss border-t-transparent"
      />
      {label}
    </div>
  );
}

/** Khối xám nhấp nháy thay cho nội dung đang tải, giữ đúng chiều cao để trang không nhảy. */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded bg-line", className)} />;
}

export function TableSkeleton({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="divide-y divide-line" aria-hidden>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-4 px-4 py-3">
          {Array.from({ length: cols }).map((_, c) => (
            <Skeleton key={c} className={cn("h-4", c === 0 ? "w-2/5" : "w-1/6")} />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * Lỗi nói rõ chuyện gì xảy ra và làm gì tiếp — không xin lỗi, không nói chung chung.
 */
export function ErrorState({
  message,
  onRetry,
  className,
}: {
  message: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={cn(
        "flex items-start gap-3 rounded-[var(--radius-control)] border border-danger/30 bg-danger-wash px-4 py-3",
        className,
      )}
    >
      <AlertTriangle aria-hidden className="mt-0.5 size-4 shrink-0 text-danger" />
      <div className="min-w-0 flex-1">
        <p className="text-sm text-ink">{message}</p>
        {onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry} className="mt-2">
            Tải lại
          </Button>
        )}
      </div>
    </div>
  );
}

/** Màn hình rỗng là lời mời hành động, không phải thông báo buồn. */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <Inbox aria-hidden className="size-6 text-bark" />
      <p className="mt-3 font-medium text-ink">{title}</p>
      {description && <p className="mt-1 max-w-sm text-sm text-bark">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

/**
 * Chỗ dành cho màn hình mà backend chưa có API. Nói thẳng endpoint còn thiếu thay vì
 * dựng số liệu giả — người xem biết ngay đây là việc chưa làm, không phải lỗi.
 */
export function PendingApi({ endpoint }: { endpoint: MissingEndpoint }) {
  return (
    <div className="rounded-[var(--radius-control)] border border-dashed border-line-strong bg-surface px-5 py-8">
      <div className="flex items-start gap-3">
        <Wrench aria-hidden className="mt-0.5 size-4 shrink-0 text-bark" />
        <div className="min-w-0">
          <p className="font-medium text-ink">{endpoint.label} chưa có dữ liệu</p>
          <p className="mt-1 text-sm text-bark">{endpoint.reason}</p>
          <p className="mt-3 text-xs text-bark">
            Cần backend bổ sung:{" "}
            <code className="rounded bg-paper px-1.5 py-0.5 text-ink">{endpoint.suggested}</code>
          </p>
        </div>
      </div>
    </div>
  );
}
