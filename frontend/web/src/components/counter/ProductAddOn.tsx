"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Minus, Plus, Search, Trash2 } from "lucide-react";
import { productApi } from "@/lib/api";
import { useDebouncedValue } from "@/lib/useDebouncedValue";
import { formatPrice } from "@/lib/utils/format";
import type { Product } from "@/types";
import { Button, IconButton, Spinner, TextField } from "@/components/ui";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Một sản phẩm khách mua thêm: giá và tồn kho lấy từ kho thật, số lượng không vượt tồn. */
export interface ProductLine {
  product: Product;
  quantity: number;
}

/**
 * Chọn sản phẩm của cửa hàng khách mua thêm ngay trên phiếu thu. Chỉ giữ danh sách ở phía gọi
 * (`lines`/`onChange`); lưu vào hoá đơn quầy là việc của chỗ chốt phiếu.
 */
export function ProductAddOn({
  lines,
  onChange,
}: {
  lines: ProductLine[];
  onChange: (next: ProductLine[]) => void;
}) {
  const [keyword, setKeyword] = useState("");
  const debounced = useDebouncedValue(keyword.trim(), 300);
  const found = useQuery({
    queryKey: ["products", "counter-search", debounced],
    queryFn: () => productApi.searchLive({ keyword: debounced, size: 8 }),
    enabled: debounced.length >= 2,
  });
  // Chỉ nhận sản phẩm có id thật (UUID): backend từ chối cả hoá đơn nếu có một id lạ.
  const results = (found.data?.content ?? []).filter((p) => p.active && UUID_RE.test(p.id));

  function add(product: Product) {
    const existing = lines.find((l) => l.product.id === product.id);
    onChange(
      existing
        ? lines.map((l) =>
            l.product.id === product.id ? { ...l, quantity: Math.min(l.quantity + 1, product.stockQuantity) } : l,
          )
        : [...lines, { product, quantity: 1 }],
    );
  }

  function setQuantity(productId: string, quantity: number) {
    onChange(
      lines.map((l) =>
        l.product.id === productId
          ? { ...l, quantity: Math.max(1, Math.min(Math.floor(quantity) || 1, l.product.stockQuantity)) }
          : l,
      ),
    );
  }

  return (
    <div>
      <div className="relative">
        <Search aria-hidden className="pointer-events-none absolute top-[2.15rem] left-3 size-4 text-bark" />
        <TextField
          label="Tìm sản phẩm"
          placeholder="Tên hoặc mã SKU, ít nhất 2 ký tự"
          className="pl-9"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>

      {debounced.length >= 2 && (
        <div className="mt-2 rounded-[var(--radius-control)] border border-line">
          {found.isLoading ? (
            <div className="p-3">
              <Spinner />
            </div>
          ) : found.isError ? (
            <p className="p-3 text-sm text-danger">
              Không tải được danh sách sản phẩm (kho hàng chưa sẵn sàng). Thử lại sau ít phút.
            </p>
          ) : results.length === 0 ? (
            <p className="p-3 text-sm text-bark">Không tìm thấy sản phẩm nào đang bán.</p>
          ) : (
            <ul className="divide-y divide-line">
              {results.map((p) => {
                const out = p.stockQuantity <= 0;
                return (
                  <li key={p.id} className="flex items-center gap-3 px-3 py-2">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink">{p.name}</p>
                      <p className="text-xs text-bark">
                        {p.sku} · còn {p.stockQuantity} {p.unit}
                      </p>
                    </div>
                    <span className="tnum text-sm">{formatPrice(p.price)}</span>
                    <Button size="sm" variant="secondary" disabled={out} onClick={() => add(p)}>
                      {out ? "Hết hàng" : "Thêm"}
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}

      {lines.length > 0 && (
        <ul className="mt-3 divide-y divide-line rounded-[var(--radius-control)] border border-line">
          {lines.map((l) => (
            <li key={l.product.id} className="flex flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2.5">
              <div className="min-w-32 flex-1">
                <p className="text-sm font-medium text-ink">{l.product.name}</p>
                <p className="text-xs text-bark">
                  {formatPrice(l.product.price)} / {l.product.unit}
                </p>
              </div>
              <div className="flex items-center gap-1">
                <IconButton
                  label="Giảm số lượng"
                  size="sm"
                  variant="secondary"
                  disabled={l.quantity <= 1}
                  onClick={() => setQuantity(l.product.id, l.quantity - 1)}
                >
                  <Minus aria-hidden className="size-3.5" />
                </IconButton>
                <input
                  aria-label={`Số lượng ${l.product.name}`}
                  type="number"
                  min={1}
                  max={l.product.stockQuantity}
                  value={l.quantity}
                  onChange={(e) => setQuantity(l.product.id, Number(e.target.value))}
                  className="tnum h-8 w-14 rounded-[var(--radius-control)] border border-line-strong bg-surface text-center text-sm"
                />
                <IconButton
                  label="Tăng số lượng"
                  size="sm"
                  variant="secondary"
                  disabled={l.quantity >= l.product.stockQuantity}
                  onClick={() => setQuantity(l.product.id, l.quantity + 1)}
                >
                  <Plus aria-hidden className="size-3.5" />
                </IconButton>
              </div>
              <span className="tnum w-24 text-right text-sm font-medium">
                {formatPrice(l.product.price * l.quantity)}
              </span>
              <IconButton
                label={`Bỏ ${l.product.name}`}
                size="sm"
                variant="ghost"
                onClick={() => onChange(lines.filter((x) => x.product.id !== l.product.id))}
              >
                <Trash2 aria-hidden className="size-4" />
              </IconButton>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
