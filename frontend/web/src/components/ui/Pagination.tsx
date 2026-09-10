"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button, IconButton } from "./Button";

/**
 * Phân trang cho PageResponse của backend. Hiện luôn tổng số bản ghi vì trong phần mềm
 * quản lý, "có bao nhiêu" thường là thông tin người dùng cần trước cả nội dung trang.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  unitLabel,
  onChange,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  /** Danh từ đếm được: "sản phẩm", "đơn hàng". */
  unitLabel: string;
  onChange: (page: number) => void;
}) {
  if (totalElements === 0) return null;

  return (
    <nav
      aria-label="Phân trang"
      className="flex flex-wrap items-center gap-3 border-t border-line px-4 py-3 text-sm"
    >
      <p className="text-bark">
        <span className="tnum text-ink">{totalElements}</span> {unitLabel}
      </p>

      {totalPages > 1 && (
        <div className="ml-auto flex items-center gap-2">
          <IconButton
            label="Trang trước"
            variant="secondary"
            size="sm"
            disabled={page <= 0}
            onClick={() => onChange(page - 1)}
          >
            <ChevronLeft aria-hidden className="size-4" />
          </IconButton>
          <span className="tnum text-bark" aria-current="page">
            {page + 1} / {totalPages}
          </span>
          <IconButton
            label="Trang sau"
            variant="secondary"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => onChange(page + 1)}
          >
            <ChevronRight aria-hidden className="size-4" />
          </IconButton>
        </div>
      )}
    </nav>
  );
}

/** Nút tải thêm cho danh sách không phân trang phía backend. */
export function LoadMore({ onClick, loading }: { onClick: () => void; loading?: boolean }) {
  return (
    <div className="border-t border-line px-4 py-3 text-center">
      <Button variant="secondary" size="sm" onClick={onClick} loading={loading}>
        Tải thêm
      </Button>
    </div>
  );
}
