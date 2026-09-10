"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Boxes, History } from "lucide-react";
import { ApiError, categoryApi, productApi } from "@/lib/api";
import { STOCK_STATUS } from "@/lib/utils/status";
import { formatDateTime } from "@/lib/utils/format";
import { stockStatusOf, type Product, type StockMovement, type StockStatus } from "@/types";
import { StockDialog } from "@/components/product/StockDialog";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  IconButton,
  Pagination,
  Spinner,
  StatusTag,
  TableFrame,
  TableSkeleton,
  controlClass,
  type Column,
} from "@/components/ui";

const FILTERS: Array<{ value: StockStatus | "ALL"; label: string }> = [
  { value: "ALL", label: "Tất cả" },
  { value: "LOW_STOCK", label: "Sắp hết" },
  { value: "OUT_OF_STOCK", label: "Hết hàng" },
  { value: "IN_STOCK", label: "Còn đủ" },
];

/**
 * Màn hình kho dùng chung cho quản trị và nhân viên — cả hai có cùng quyền thao tác kho ở
 * backend, nên tách ra đây thay vì viết hai bản gần giống nhau.
 *
 * Lọc theo tình trạng tồn làm ở phía giao diện: backend chỉ có `/products/low-stock` chứ
 * không nhận tham số lọc theo tình trạng, nên lọc trong trang hiện tại là đủ và trung thực.
 */
export function InventoryView() {
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [applied, setApplied] = useState({ keyword: "", categoryId: "" });
  const [statusFilter, setStatusFilter] = useState<StockStatus | "ALL">("ALL");

  const [stockFor, setStockFor] = useState<Product | null>(null);
  const [historyFor, setHistoryFor] = useState<Product | null>(null);

  const categories = useQuery({ queryKey: ["categories"], queryFn: categoryApi.list });
  const products = useQuery({
    queryKey: ["products", { page, ...applied }],
    queryFn: () => productApi.search({ page, size: 20, ...applied }),
  });

  const rows = (products.data?.content ?? []).filter((p) =>
    statusFilter === "ALL" ? true : stockStatusOf(p) === statusFilter,
  );

  const columns: Column<Product>[] = [
    {
      key: "name",
      header: "Sản phẩm",
      cell: (p) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-ink">{p.name}</p>
          <p className="text-xs text-bark">
            {p.sku} · {p.categoryName}
          </p>
        </div>
      ),
    },
    {
      key: "stock",
      header: "Tồn hiện tại",
      numeric: true,
      cell: (p) => (
        <span className="whitespace-nowrap font-medium">
          {p.stockQuantity} {p.unit}
        </span>
      ),
    },
    {
      key: "threshold",
      header: "Ngưỡng",
      numeric: true,
      hideBelow: "lg",
      cell: (p) => <span className="text-bark">{p.lowStockThreshold ?? "—"}</span>,
    },
    {
      key: "status",
      header: "Tình trạng",
      cell: (p) => <StatusTag status={STOCK_STATUS[stockStatusOf(p)]} />,
    },
    {
      key: "updated",
      header: "Cập nhật",
      hideBelow: "lg",
      cell: (p) => <span className="text-bark">{formatDateTime(p.updatedAt)}</span>,
    },
    {
      key: "actions",
      header: "Thao tác",
      cell: (p) => (
        <div className="flex justify-end gap-1">
          <IconButton label={`Nhập kho ${p.name}`} variant="ghost" size="sm" onClick={() => setStockFor(p)}>
            <Boxes aria-hidden className="size-4" />
          </IconButton>
          <IconButton
            label={`Lịch sử kho ${p.name}`}
            variant="ghost"
            size="sm"
            onClick={() => setHistoryFor(p)}
          >
            <History aria-hidden className="size-4" />
          </IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setApplied({ keyword, categoryId });
        }}
        className="mb-4 flex flex-wrap items-end gap-3"
      >
        <div className="min-w-52 flex-1">
          <label htmlFor="inv-kw" className="mb-1.5 block text-sm font-medium">
            Tìm theo tên
          </label>
          <input
            id="inv-kw"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tên sản phẩm"
            className={`${controlClass} h-9`}
          />
        </div>
        <div className="min-w-40">
          <label htmlFor="inv-cat" className="mb-1.5 block text-sm font-medium">
            Danh mục
          </label>
          <select
            id="inv-cat"
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            className={`${controlClass} h-9`}
          >
            <option value="">Tất cả</option>
            {(categories.data ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" variant="secondary">
          Tìm
        </Button>
      </form>

      <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="Lọc theo tình trạng tồn">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setStatusFilter(f.value)}
            aria-pressed={statusFilter === f.value}
            className={`rounded-[var(--radius-control)] border px-3 py-1.5 text-sm transition-colors ${
              statusFilter === f.value
                ? "border-moss bg-moss-wash font-medium text-ink"
                : "border-line-strong bg-surface text-ink-soft hover:bg-paper"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      <TableFrame title="Tồn kho" count={rows.length}>
        {products.isLoading ? (
          <TableSkeleton rows={6} cols={5} />
        ) : products.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                products.error instanceof ApiError ? products.error.message : "Không tải được tồn kho."
              }
              onRetry={() => products.refetch()}
            />
          </div>
        ) : (
          <>
            <DataTable
              caption="Tồn kho theo sản phẩm"
              rows={rows}
              keyOf={(p) => p.id}
              columns={columns}
              empty={
                <EmptyState
                  title={
                    statusFilter === "ALL" ? "Chưa có sản phẩm nào" : "Không mặt hàng nào ở tình trạng này"
                  }
                />
              }
            />
            {products.data && statusFilter === "ALL" && (
              <Pagination
                page={products.data.page}
                totalPages={products.data.totalPages}
                totalElements={products.data.totalElements}
                unitLabel="sản phẩm"
                onChange={setPage}
              />
            )}
          </>
        )}
      </TableFrame>

      <StockDialog product={stockFor} onClose={() => setStockFor(null)} />
      <StockHistoryDialog product={historyFor} onClose={() => setHistoryFor(null)} />
    </>
  );
}

const MOVEMENT_LABEL: Record<StockMovement["type"], string> = {
  IMPORT: "Nhập kho",
  ADJUSTMENT: "Điều chỉnh",
  SALE: "Bán ra",
  RETURN: "Hoàn kho",
};

function StockHistoryDialog({ product, onClose }: { product: Product | null; onClose: () => void }) {
  const movements = useQuery({
    queryKey: ["stock-movements", product?.id],
    queryFn: () => productApi.stockMovements(product!.id),
    enabled: product !== null,
  });

  return (
    <Dialog
      open={product !== null}
      onClose={onClose}
      title="Lịch sử biến động kho"
      description={product ? product.name : undefined}
      footer={
        <Button variant="secondary" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      {movements.isLoading ? (
        <Spinner />
      ) : movements.isError ? (
        <ErrorState message="Không tải được lịch sử." onRetry={() => movements.refetch()} />
      ) : (movements.data ?? []).length === 0 ? (
        <p className="py-6 text-center text-sm text-bark">Mặt hàng này chưa có biến động nào.</p>
      ) : (
        <ol className="divide-y divide-line text-sm">
          {(movements.data ?? []).map((m) => (
            <li key={m.id} className="flex flex-wrap items-baseline gap-x-3 gap-y-1 py-2.5">
              <span className="font-medium text-ink">{MOVEMENT_LABEL[m.type]}</span>
              <span
                className={`tnum font-medium ${m.quantityChange > 0 ? "text-moss" : "text-danger"}`}
              >
                {m.quantityChange > 0 ? "+" : ""}
                {m.quantityChange}
              </span>
              <span className="tnum text-bark">còn {m.quantityAfter}</span>
              <span className="ml-auto text-xs text-bark">{formatDateTime(m.createdAt)}</span>
              {m.note && <p className="w-full text-xs text-bark">{m.note}</p>}
            </li>
          ))}
        </ol>
      )}
    </Dialog>
  );
}

/** Ô cảnh báo hàng sắp hết, dùng ở đầu trang kho. */
export function LowStockBanner() {
  const lowStock = useQuery({ queryKey: ["products", "low-stock"], queryFn: productApi.lowStock });
  const count = lowStock.data?.length ?? 0;
  if (lowStock.isLoading || count === 0) return null;

  return (
    <div className="mb-4 rounded-[var(--radius-control)] border border-amber/40 bg-amber-wash px-4 py-3 text-sm">
      <strong className="font-medium">{count} mặt hàng</strong> đã xuống dưới ngưỡng cảnh báo:{" "}
      {(lowStock.data ?? [])
        .slice(0, 4)
        .map((p) => `${p.name} (${p.stockQuantity} ${p.unit})`)
        .join(", ")}
      {count > 4 && `, và ${count - 4} mặt hàng khác`}
    </div>
  );
}
