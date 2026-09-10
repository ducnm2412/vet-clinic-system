"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, Minus, Plus, ShieldCheck, Truck } from "lucide-react";
import { productApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { ButtonLink, Container } from "@/components/site/primitives";
import { AddToCartButton, ProductCard, ProductImage, StockLine } from "@/components/site/ProductCard";
import type { Product } from "@/types";

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();

  const product = useQuery({
    queryKey: ["products", id],
    queryFn: () => productApi.byId(id),
  });

  if (product.isLoading) return <DetailSkeleton />;

  if (product.isError || !product.data) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Không tìm thấy sản phẩm này</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Có thể phòng khám đã ngừng bán, hoặc đường dẫn bị sai một ký tự.
        </p>
        <div className="mt-8">
          <ButtonLink href="/products" variant="outline">
            Về danh sách sản phẩm
          </ButtonLink>
        </div>
      </Container>
    );
  }

  return <ProductBody product={product.data} />;
}

function ProductBody({ product }: { product: Product }) {
  const [quantity, setQuantity] = useState(1);

  const max = Math.max(1, product.stockQuantity);
  const out = product.stockQuantity <= 0;

  return (
    <>
      <Container className="pt-8">
        <Link
          href="/products"
          className="inline-flex items-center gap-1.5 text-[15px] text-stone hover:text-pine"
        >
          <ChevronLeft aria-hidden className="size-4" />
          Tất cả sản phẩm
        </Link>
      </Container>

      <Container className="grid gap-10 py-8 md:grid-cols-2 md:gap-16 md:py-12">
        {/*
          Chỉ có một ảnh cho mỗi sản phẩm: `ProductResponse` có đúng một trường `imageUrl`.
          Nên không dựng dải ảnh thu nhỏ giả vờ là bộ sưu tập nhiều góc chụp.
        */}
        {/*
          Giới hạn bề ngang: để nguyên cả cột thì mái vòm cao gần 700px, một món hàng
          không có ảnh biến thành mảng màu chiếm hết màn hình.
        */}
        <div className="group md:sticky md:top-24 md:self-start">
          <ProductImage product={product} className="aspect-[4/5] w-full max-w-[26rem]" />
        </div>

        <div>
          <p className="text-stone">{product.categoryName}</p>
          <h1 className="t-h2 mt-2">{product.name}</h1>

          <p className="tnum mt-6 text-[32px] font-semibold leading-none text-pine">
            {formatPrice(product.price)}
            <span className="ml-2 text-[17px] font-normal text-stone">/ {product.unit}</span>
          </p>
          <div className="mt-2">
            <StockLine product={product} />
          </div>

          {product.description && (
            <p className="measure t-body mt-7 text-stone">{product.description}</p>
          )}

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <QuantityStepper
              value={quantity}
              max={max}
              disabled={out}
              onChange={setQuantity}
              productName={product.name}
            />
            <AddToCartButton product={product} quantity={quantity} size="lg" />
          </div>

          {!out && product.stockQuantity < quantity && (
            <p className="mt-3 text-sm text-coral-deep">
              Kho chỉ còn {product.stockQuantity} {product.unit}. Giảm số lượng giúp bạn nhé.
            </p>
          )}

          <dl className="mt-10 divide-y divide-mist border-y border-mist">
            <Row label="Mã sản phẩm" value={product.sku} />
            <Row label="Đơn vị" value={product.unit} />
            <Row label="Danh mục" value={product.categoryName} />
          </dl>

          <ul className="mt-8 space-y-3 text-[15px] text-stone">
            <li className="flex gap-3">
              <ShieldCheck aria-hidden className="mt-0.5 size-5 shrink-0 text-teal" />
              Hàng lấy từ kho phòng khám, có ghi vết từng lần nhập.
            </li>
            <li className="flex gap-3">
              <Truck aria-hidden className="mt-0.5 size-5 shrink-0 text-teal" />
              Thanh toán khi nhận hàng. Phí giao tính khi phòng khám xác nhận đơn.
            </li>
          </ul>
        </div>
      </Container>

      <RelatedProducts product={product} />

      {/* Thanh hành động dính đáy trên điện thoại — cuộn đến đâu vẫn mua được. */}
      <div className="sticky bottom-0 z-30 border-t border-mist bg-white/95 px-5 py-3 backdrop-blur md:hidden">
        <div className="flex items-center gap-4">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-stone">{product.name}</p>
            <p className="tnum font-semibold text-pine">{formatPrice(product.price)}</p>
          </div>
          <AddToCartButton product={product} quantity={quantity} />
        </div>
      </div>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-6 py-3">
      <dt className="text-stone">{label}</dt>
      <dd className="text-right text-pine">{value}</dd>
    </div>
  );
}

/** Nút tròn 44px thay vì ô nhập số — trên điện thoại bấm dễ hơn hẳn, không cần bàn phím. */
function QuantityStepper({
  value,
  max,
  disabled,
  onChange,
  productName,
}: {
  value: number;
  max: number;
  disabled?: boolean;
  onChange: (v: number) => void;
  productName: string;
}) {
  return (
    <div className="flex items-center gap-1 rounded-full border border-mist p-1">
      <button
        onClick={() => onChange(Math.max(1, value - 1))}
        disabled={disabled || value <= 1}
        aria-label={`Bớt một ${productName}`}
        className="grid size-11 place-items-center rounded-full text-pine transition-colors hover:bg-mint disabled:opacity-35 disabled:hover:bg-transparent"
      >
        <Minus aria-hidden className="size-4" />
      </button>
      <span className="tnum w-10 text-center text-[17px] font-medium" aria-live="polite">
        {value}
      </span>
      <button
        onClick={() => onChange(Math.min(max, value + 1))}
        disabled={disabled || value >= max}
        aria-label={`Thêm một ${productName}`}
        className="grid size-11 place-items-center rounded-full text-pine transition-colors hover:bg-mint disabled:opacity-35 disabled:hover:bg-transparent"
      >
        <Plus aria-hidden className="size-4" />
      </button>
    </div>
  );
}

function RelatedProducts({ product }: { product: Product }) {
  const related = useQuery({
    queryKey: ["products", { categoryId: product.categoryId, size: 5 }],
    queryFn: () => productApi.search({ categoryId: product.categoryId, size: 5 }),
  });

  const list = (related.data?.content ?? []).filter((p) => p.id !== product.id).slice(0, 4);
  if (list.length === 0) return null;

  return (
    <section className="bg-mint py-16 md:py-24">
      <Container>
        <h2 className="t-h2">Cùng nhóm {product.categoryName.toLowerCase()}</h2>
        <ul className="mt-10 grid grid-cols-2 gap-x-6 gap-y-10 lg:grid-cols-4">
          {list.map((p) => (
            <li key={p.id}>
              <ProductCard product={p} />
            </li>
          ))}
        </ul>
      </Container>
    </section>
  );
}

function DetailSkeleton() {
  return (
    <Container className="grid gap-10 py-12 md:grid-cols-2 md:gap-16">
      <div className="arch aspect-[4/5] w-full animate-pulse bg-mint" />
      <div className="space-y-4 pt-4">
        <div className="h-4 w-1/4 animate-pulse rounded bg-mint" />
        <div className="h-10 w-3/4 animate-pulse rounded bg-mint" />
        <div className="h-8 w-1/3 animate-pulse rounded bg-mint" />
        <div className="h-24 w-full animate-pulse rounded bg-mint" />
      </div>
    </Container>
  );
}
