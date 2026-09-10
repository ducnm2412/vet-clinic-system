"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShoppingCart } from "lucide-react";
import { ApiError, cartApi, categoryApi, productApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import type { Product } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  EmptyState,
  ErrorState,
  Pagination,
  Skeleton,
  Tag,
  controlClass,
  useToast,
} from "@/components/ui";

/**
 * Trang mua sắm của khách. Giá và tồn hiển thị ở đây chỉ để tham khảo — lúc thêm vào giỏ
 * và lúc đặt hàng, order-service đều hỏi lại product-service, nên con số phía client
 * không bao giờ là căn cứ.
 */
export default function CustomerShopPage() {
  const qc = useQueryClient();
  const toast = useToast();

  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [applied, setApplied] = useState({ keyword: "", categoryId: "" });

  const categories = useQuery({ queryKey: ["categories"], queryFn: categoryApi.list });
  const products = useQuery({
    queryKey: ["products", { page, ...applied }],
    queryFn: () => productApi.search({ page, size: 12, ...applied }),
  });
  const cart = useQuery({ queryKey: ["cart"], queryFn: cartApi.get });

  const add = useMutation({
    mutationFn: (product: Product) => cartApi.addItem({ productId: product.id, quantity: 1 }),
    onSuccess: (_data, product) => {
      qc.invalidateQueries({ queryKey: ["cart"] });
      toast.success(`Đã thêm ${product.name} vào giỏ`);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không thêm được vào giỏ."),
  });

  const cartCount = cart.data?.totalItems ?? 0;

  return (
    <>
      <PageHeader
        title="Mua sắm"
        description="Thức ăn, thuốc và phụ kiện cho thú cưng."
        actions={
          <Link href="/customer/cart">
            <Button variant="secondary">
              <ShoppingCart aria-hidden className="size-4" />
              Giỏ hàng
              {cartCount > 0 && <span className="tnum">({cartCount})</span>}
            </Button>
          </Link>
        }
      />

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setApplied({ keyword, categoryId });
        }}
        className="mb-5 flex flex-wrap items-end gap-3"
      >
        <div className="min-w-52 flex-1">
          <label htmlFor="shop-kw" className="mb-1.5 block text-sm font-medium">
            Tìm sản phẩm
          </label>
          <input
            id="shop-kw"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tên sản phẩm"
            className={`${controlClass} h-9`}
          />
        </div>
        <div className="min-w-44">
          <label htmlFor="shop-cat" className="mb-1.5 block text-sm font-medium">
            Danh mục
          </label>
          <select
            id="shop-cat"
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

      {products.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-44" />
          <Skeleton className="h-44" />
          <Skeleton className="h-44" />
        </div>
      ) : products.isError ? (
        <ErrorState
          message={
            products.error instanceof ApiError ? products.error.message : "Không tải được sản phẩm."
          }
          onRetry={() => products.refetch()}
        />
      ) : (products.data?.content ?? []).length === 0 ? (
        <div className="rounded-[var(--radius-control)] border border-line bg-surface">
          <EmptyState
            title="Không tìm thấy sản phẩm nào"
            description="Thử bỏ bớt điều kiện lọc."
          />
        </div>
      ) : (
        <>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {(products.data?.content ?? []).map((p) => {
              const soldOut = p.stockQuantity <= 0;
              return (
                <li
                  key={p.id}
                  className="flex flex-col rounded-[var(--radius-control)] border border-line bg-surface p-4"
                >
                  <div className="flex items-start justify-between gap-2">
                    <h2 className="font-medium text-ink">{p.name}</h2>
                    {soldOut && <Tag className="text-danger">Hết hàng</Tag>}
                  </div>
                  <p className="mt-0.5 text-sm text-bark">{p.categoryName}</p>

                  {p.description && (
                    <p className="mt-2 line-clamp-2 text-sm text-ink-soft">{p.description}</p>
                  )}

                  <div className="mt-auto pt-4">
                    <p className="font-[family-name:var(--font-display)] text-[20px] text-ink tnum">
                      {formatPrice(p.price)}
                      <span className="ml-1 text-sm font-normal text-bark">/ {p.unit}</span>
                    </p>
                    <Button
                      className="mt-3 w-full"
                      disabled={soldOut}
                      loading={add.isPending && add.variables?.id === p.id}
                      onClick={() => add.mutate(p)}
                    >
                      {soldOut ? "Hết hàng" : "Thêm vào giỏ"}
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>

          {products.data && (
            <div className="mt-4 rounded-[var(--radius-control)] border border-line bg-surface">
              <Pagination
                page={products.data.page}
                totalPages={products.data.totalPages}
                totalElements={products.data.totalElements}
                unitLabel="sản phẩm"
                onChange={setPage}
              />
            </div>
          )}
        </>
      )}
    </>
  );
}
