"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils/cn";

export interface Column<T> {
  key: string;
  header: string;
  /** Nội dung ô. Nhận cả hàng để tự quyết cách hiển thị. */
  cell: (row: T) => ReactNode;
  /** Số liệu căn phải để thẳng cột. */
  numeric?: boolean;
  /**
   * Ẩn cột này trên màn hình hẹp. Cột bị ẩn vẫn xuất hiện trong thẻ ở chế độ điện thoại,
   * nên thông tin không mất đi — chỉ đổi cách trình bày.
   */
  hideBelow?: "sm" | "md" | "lg";
}

const HIDE: Record<NonNullable<Column<unknown>["hideBelow"]>, string> = {
  sm: "hidden sm:table-cell",
  md: "hidden md:table-cell",
  lg: "hidden lg:table-cell",
};

/**
 * Bảng cho màn hình rộng, danh sách thẻ có nhãn cho điện thoại — không dùng cuộn ngang,
 * vì cuộn ngang trên điện thoại là cách chắc chắn khiến người dùng bỏ sót cột.
 */
export function DataTable<T>({
  columns,
  rows,
  keyOf,
  onRowClick,
  caption,
  empty,
}: {
  columns: Column<T>[];
  rows: T[];
  keyOf: (row: T) => string;
  onRowClick?: (row: T) => void;
  /** Mô tả bảng cho trình đọc màn hình. */
  caption: string;
  empty?: ReactNode;
}) {
  if (rows.length === 0 && empty) return <>{empty}</>;

  return (
    <>
      <table className="hidden w-full border-collapse text-sm md:table">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-line-strong text-left">
            {columns.map((c) => (
              <th
                key={c.key}
                scope="col"
                className={cn(
                  "px-4 py-2.5 font-medium text-bark",
                  c.numeric && "text-right",
                  c.hideBelow && HIDE[c.hideBelow],
                )}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((row) => (
            <tr
              key={keyOf(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              tabIndex={onRowClick ? 0 : undefined}
              onKeyDown={
                onRowClick
                  ? (e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onRowClick(row);
                      }
                    }
                  : undefined
              }
              className={cn(
                "bg-surface",
                onRowClick && "cursor-pointer hover:bg-paper focus-visible:bg-paper",
              )}
            >
              {columns.map((c) => (
                <td
                  key={c.key}
                  className={cn(
                    "px-4 py-2.5 align-middle",
                    c.numeric && "text-right tnum",
                    c.hideBelow && HIDE[c.hideBelow],
                  )}
                >
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {/* Điện thoại: mỗi hàng thành một khối, mỗi ô có nhãn của chính nó. */}
      <ul className="divide-y divide-line md:hidden">
        {rows.map((row) => (
          <li key={keyOf(row)}>
            <div
              role={onRowClick ? "button" : undefined}
              tabIndex={onRowClick ? 0 : undefined}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              onKeyDown={
                onRowClick
                  ? (e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onRowClick(row);
                      }
                    }
                  : undefined
              }
              className={cn("bg-surface px-4 py-3", onRowClick && "cursor-pointer active:bg-paper")}
            >
              <p className="font-medium text-ink">{columns[0]?.cell(row)}</p>
              <dl className="mt-1.5 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-[13px]">
                {columns.slice(1).map((c) => (
                  <div key={c.key} className="col-span-2 grid grid-cols-subgrid">
                    <dt className="text-bark">{c.header}</dt>
                    <dd className={cn("text-ink", c.numeric && "tnum")}>{c.cell(row)}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}

/** Khung có viền bao quanh bảng, kèm tiêu đề và vùng hành động. */
export function TableFrame({
  title,
  count,
  actions,
  children,
}: {
  title: string;
  count?: number;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface">
      <header className="flex flex-wrap items-center gap-3 border-b border-line px-4 py-3">
        <h2 className="font-medium text-ink">{title}</h2>
        {count !== undefined && <span className="text-sm text-bark tnum">{count}</span>}
        {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
      </header>
      {children}
    </section>
  );
}
