"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Boxes, Eye, EyeOff, Pencil, Plus, Tags } from "lucide-react";
import { ApiError, categoryApi, productApi } from "@/lib/api";
import { STOCK_STATUS } from "@/lib/utils/status";
import { formatPrice } from "@/lib/utils/format";
import { stockStatusOf, type Product } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import { CategoryDialog } from "@/components/product/CategoryDialog";
import { ProductFormDialog } from "@/components/product/ProductFormDialog";
import { StockDialog } from "@/components/product/StockDialog";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  IconButton,
  Pagination,
  StatusTag,
  TableFrame,
  TableSkeleton,
  Tag,
  controlClass,
  useToast,
  type Column,
} from "@/components/ui";

export default function AdminProductsPage() {
  const qc = useQueryClient();
  const toast = useToast();

  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [applied, setApplied] = useState({ keyword: "", categoryId: "" });

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Product | null>(null);
  const [stockFor, setStockFor] = useState<Product | null>(null);
  const [categoriesOpen, setCategoriesOpen] = useState(false);

  const categories = useQuery({ queryKey: ["categories"], queryFn: categoryApi.list });

  const products = useQuery({
    queryKey: ["products", { page, ...applied }],
    queryFn: () => productApi.search({ page, size: 20, ...applied }),
  });

  // VD-03: không xoá sản phẩm, chỉ ẩn khỏi cửa hàng — xoá sẽ mất luôn lịch sử kho và làm
  // đơn hàng cũ trỏ vào khoảng không.
  const toggleVisibility = useMutation({
    mutationFn: (p: Product) => (p.active ? productApi.hide(p.id) : productApi.unhide(p.id)),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ["products"] });
      toast.success(saved.active ? `Đã bán lại ${saved.name}` : `Đã ẩn ${saved.name} khỏi cửa hàng`);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không đổi được trạng thái bán."),
  });

  function openCreate() {
    setEditing(null);
    setFormOpen(true);
  }

  function openEdit(p: Product) {
    setEditing(p);
    setFormOpen(true);
  }

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
      key: "active",
      header: "Trạng thái bán",
      hideBelow: "lg",
      cell: (p) => (p.active ? <Tag>Đang bán</Tag> : <Tag className="text-bark">Đã ẩn</Tag>),
    },
    { key: "price", header: "Giá", numeric: true, cell: (p) => formatPrice(p.price) },
    {
      key: "stock",
      header: "Tồn kho",
      numeric: true,
      cell: (p) => (
        <span className="whitespace-nowrap">
          {p.stockQuantity} {p.unit}
        </span>
      ),
    },
    {
      key: "stockStatus",
      header: "Tình trạng",
      cell: (p) => <StatusTag status={STOCK_STATUS[stockStatusOf(p)]} />,
    },
    {
      key: "actions",
      header: "Thao tác",
      cell: (p) => (
        <div className="flex justify-end gap-1">
          <IconButton label={`Nhập kho ${p.name}`} variant="ghost" size="sm" onClick={() => setStockFor(p)}>
            <Boxes aria-hidden className="size-4" />
          </IconButton>
          <IconButton label={`Sửa ${p.name}`} variant="ghost" size="sm" onClick={() => openEdit(p)}>
            <Pencil aria-hidden className="size-4" />
          </IconButton>
          <IconButton
            label={p.active ? `Ẩn ${p.name} khỏi cửa hàng` : `Bán lại ${p.name}`}
            variant="ghost"
            size="sm"
            onClick={() => toggleVisibility.mutate(p)}
          >
            {p.active ? (
              <EyeOff aria-hidden className="size-4" />
            ) : (
              <Eye aria-hidden className="size-4" />
            )}
          </IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Sản phẩm"
        description="Thức ăn, thuốc và phụ kiện đang bán tại phòng khám."
        actions={
          <div className="flex flex-wrap gap-2">
            {/* CN-27: danh mục là danh sách ngắn, ít khi sửa — để cạnh chỗ dùng nó, không cần
                một mục riêng trong thanh điều hướng. */}
            <Button variant="secondary" onClick={() => setCategoriesOpen(true)}>
              <Tags aria-hidden className="size-4" />
              Danh mục
            </Button>
            <Button onClick={openCreate}>
              <Plus aria-hidden className="size-4" />
              Thêm sản phẩm
            </Button>
          </div>
        }
      />

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setApplied({ keyword, categoryId });
        }}
        className="mb-4 flex flex-wrap items-end gap-3"
      >
        <div className="min-w-52 flex-1">
          <label htmlFor="kw" className="mb-1.5 block text-sm font-medium">
            Tìm theo tên
          </label>
          <input
            id="kw"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tên sản phẩm"
            className={`${controlClass} h-9`}
          />
        </div>
        <div className="min-w-44">
          <label htmlFor="cat" className="mb-1.5 block text-sm font-medium">
            Danh mục
          </label>
          <select
            id="cat"
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

      <TableFrame title="Danh sách sản phẩm">
        {products.isLoading ? (
          <TableSkeleton rows={6} cols={5} />
        ) : products.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                products.error instanceof ApiError
                  ? products.error.message
                  : "Không tải được danh sách sản phẩm."
              }
              onRetry={() => products.refetch()}
            />
          </div>
        ) : (
          <>
            <DataTable
              caption="Danh sách sản phẩm"
              rows={products.data?.content ?? []}
              keyOf={(p) => p.id}
              columns={columns}
              empty={
                <EmptyState
                  title={applied.keyword || applied.categoryId ? "Không có sản phẩm nào khớp" : "Chưa có sản phẩm nào"}
                  description={
                    applied.keyword || applied.categoryId
                      ? "Thử bỏ bớt điều kiện lọc."
                      : "Thêm mặt hàng đầu tiên để bắt đầu bán."
                  }
                  action={<Button size="sm" onClick={openCreate}>Thêm sản phẩm</Button>}
                />
              }
            />
            {products.data && (
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

      <ProductFormDialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        categories={categories.data ?? []}
        editing={editing}
      />
      <StockDialog product={stockFor} onClose={() => setStockFor(null)} />

      <CategoryDialog open={categoriesOpen} onClose={() => setCategoriesOpen(false)} />
    </>
  );
}
