"use client";

/*
  Chi tiết sản phẩm — tông cam/kem/xanh rêu giống trang chủ & trang dịch vụ.
  Khung: breadcrumb → thẻ sản phẩm (ảnh nền cam + thông tin) → tab nội dung + khối thông số xanh
  → dải "Cùng nhóm" nền xanh lá nhạt.
*/

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  Barcode,
  CheckCircle2,
  ChevronLeft,
  Globe2,
  Heart,
  Leaf,
  Minus,
  PawPrint,
  Plus,
  Star,
  Warehouse,
} from "lucide-react";
import { productApi } from "@/lib/api";
import { productContent } from "@/lib/content/product-content"; // MOCK-TẠM
import { formatPrice } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { AddToCartButton } from "@/components/site/ProductCard";
import { Paw } from "@/components/site/Decor";
import type { Product } from "@/types";

const ORANGE = "#f26b1d";
const GREEN = "#1f4a3c";
const INK = "#2b1d14";
const ORANGE_BTN = "!rounded-full !bg-[#f26b1d] !text-white hover:!bg-[#e05a10] !border-0";

export default function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const product = useQuery({ queryKey: ["products", id], queryFn: () => productApi.byId(id) });

  if (product.isLoading) return <DetailSkeleton />;

  if (product.isError || !product.data) {
    return (
      <div className="mx-auto max-w-xl px-6 py-24 text-center">
        <h1 className="text-2xl font-bold" style={{ color: INK }}>Không tìm thấy sản phẩm này</h1>
        <p className="mt-4 text-[#6b5a4e]">Có thể phòng khám đã ngừng bán, hoặc đường dẫn bị sai một ký tự.</p>
        <Link href="/products" className="mt-6 inline-flex items-center gap-2 font-semibold" style={{ color: ORANGE }}>
          <ChevronLeft className="size-4" /> Về danh sách sản phẩm
        </Link>
      </div>
    );
  }

  return <ProductBody product={product.data} />;
}

function ProductBody({ product }: { product: Product }) {
  const [quantity, setQuantity] = useState(1);
  const max = Math.max(1, product.stockQuantity);
  const out = product.stockQuantity <= 0;
  const [species, group] = splitCategory(product.categoryName);

  return (
    <div className="relative overflow-hidden bg-[#fdf1e4]">
      <Paw className="left-[2%] top-[38rem] size-8 rotate-12 text-[#f9c99a]/70" />
      <Paw className="right-[3%] top-[30rem] size-7 -rotate-12 text-[#f9c99a]/70" />

      <div className="mx-auto max-w-[1440px] px-4 pb-14 sm:px-6">
        {/* Breadcrumb */}
        <nav className="flex flex-wrap items-center gap-2 pt-8 text-sm text-[#6b5a4e]">
          <Link href="/" className="hover:underline">Trang chủ</Link>
          <span>/</span>
          <Link href="/products" className="hover:underline">Sản phẩm</Link>
          <span>/</span>
          <span style={{ color: ORANGE }}>{product.categoryName}</span>
        </nav>

        {/* Thẻ sản phẩm */}
        <section className="mt-6 grid gap-8 rounded-[32px] bg-[#fffaf3] p-5 shadow-[0_10px_40px_-20px_rgba(160,90,30,0.35)] sm:p-8 md:grid-cols-[1fr_1.05fr] md:gap-12">
          <div className="relative aspect-square overflow-hidden rounded-[28px] bg-[#fbc89a]">
            <Paw className="right-6 top-8 size-14 rotate-12 text-[#f7a964]" />
            <Paw className="bottom-16 left-6 size-12 -rotate-12 text-[#f7a964]" />
            <Paw className="right-4 top-1/2 size-9 text-[#f7a964]" />
            <Heart aria-hidden className="absolute left-6 top-20 size-10 text-[#f58d4a]" />
            <Heart aria-hidden className="absolute bottom-6 right-6 size-10 fill-[#f7a964] text-[#f7a964]" />
            <span className="absolute left-4 top-4 z-10 rounded-full px-4 py-1.5 text-[15px] font-semibold text-white" style={{ background: ORANGE }}>
              Bán chạy
            </span>
            {product.imageUrl && (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={product.imageUrl} alt={product.name} className="relative z-[1] mx-auto h-full w-[78%] object-contain py-8 mix-blend-multiply" />
            )}
          </div>

          <div className="flex flex-col justify-center">
            <span className="inline-flex w-fit items-center gap-2 rounded-full bg-[#fde4cc] px-4 py-1.5 text-[15px] font-medium" style={{ color: ORANGE }}>
              <PawPrint className="size-4 fill-current" /> {species} • {group} • Dinh dưỡng cao
            </span>
            <h1 className="mt-4 text-[30px] font-extrabold leading-tight sm:text-[36px]" style={{ color: GREEN }}>
              {product.name}
            </h1>
            <p className="tnum mt-3 text-[36px] font-extrabold leading-none" style={{ color: ORANGE }}>
              {formatPrice(product.price)}
              <span className="text-[26px]"> / {product.unit}</span>
            </p>

            <span className={cn("mt-5 inline-flex w-fit items-center gap-2 rounded-full px-4 py-1.5 text-[16px] font-medium", out ? "bg-[#fde0d6] text-[#c2410c]" : "bg-[#dcebc9] text-[#1f3b2e]")}>
              <CheckCircle2 className="size-4" />
              {out ? "Tạm hết hàng" : product.stockQuantity < 5 ? `Chỉ còn ${product.stockQuantity} ${product.unit}` : "Còn hàng • Giao nhanh trong 2h"}
            </span>

            {product.description && <p className="mt-4 text-[17px]" style={{ color: INK }}>{product.description}</p>}

            <div className="mt-5 flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2">
                <RoundBtn label={`Bớt một ${product.name}`} disabled={out || quantity <= 1} onClick={() => setQuantity((q) => Math.max(1, q - 1))}>
                  <Minus className="size-5" />
                </RoundBtn>
                <span className="tnum grid h-11 w-12 place-items-center rounded-full bg-white text-lg font-semibold" aria-live="polite" style={{ color: INK }}>
                  {quantity}
                </span>
                <RoundBtn label={`Thêm một ${product.name}`} disabled={out || quantity >= max} onClick={() => setQuantity((q) => Math.min(max, q + 1))}>
                  <Plus className="size-5" />
                </RoundBtn>
              </div>
              <AddToCartButton product={product} quantity={quantity} size="lg" className={cn(ORANGE_BTN, "min-w-[14rem] flex-1 !text-[17px]")} />
            </div>

            <ul className="mt-5 space-y-3 rounded-2xl bg-[#dcebc9] px-5 py-4 text-[14px] leading-snug" style={{ color: INK }}>
              <li className="flex gap-3">
                <PawPrint className="mt-0.5 size-5 shrink-0 fill-current" style={{ color: GREEN }} />
                Hàng lấy từ kho phòng khám, có giấy tờ chứng nhận, nhập khẩu chính hãng.
              </li>
              <li className="flex gap-3">
                <PawPrint className="mt-0.5 size-5 shrink-0 fill-current" style={{ color: GREEN }} />
                Thanh toán khi nhận hàng. Freeship cho đơn từ 300k tại Cần Thơ.
              </li>
            </ul>
          </div>
        </section>

        <InfoTabs product={product} />
      </div>

      <RelatedProducts product={product} />

      {/* Thanh mua dính đáy trên điện thoại */}
      <div className="sticky bottom-0 z-30 border-t border-[#f3dcc4] bg-white/95 px-5 py-3 backdrop-blur md:hidden">
        <div className="flex items-center gap-4">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-[#6b5a4e]">{product.name}</p>
            <p className="tnum font-bold" style={{ color: ORANGE }}>{formatPrice(product.price)}</p>
          </div>
          <AddToCartButton product={product} quantity={quantity} className={ORANGE_BTN} />
        </div>
      </div>
    </div>
  );
}

function RoundBtn({ children, label, disabled, onClick }: { children: React.ReactNode; label: string; disabled?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-11 place-items-center rounded-full text-white transition hover:brightness-95 disabled:opacity-40"
      style={{ background: ORANGE }}
    >
      {children}
    </button>
  );
}

function InfoTabs({ product }: { product: Product }) {
  const c = productContent(product);
  const [tab, setTab] = useState<"desc" | "usage" | "reviews">("desc");
  const avg = c.reviews.reduce((a, r) => a + r.stars, 0) / c.reviews.length;
  const tabs = [
    { key: "desc", label: "Mô tả chi tiết" },
    { key: "usage", label: "Hướng dẫn sử dụng" },
    { key: "reviews", label: `Đánh giá (${c.reviews.length})` },
  ] as const;
  const specIcons = [Globe2, Leaf, Heart, Warehouse];

  return (
    <section className="mt-10">
      <div role="tablist" className="flex flex-wrap gap-3">
        {tabs.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setTab(t.key)}
            className={cn(
              "rounded-full px-5 py-2 text-[17px] font-semibold transition",
              tab === t.key ? "text-white" : "bg-[#ece3d8] text-[#2b1d14] hover:bg-[#e4d7c8]",
            )}
            style={tab === t.key ? { background: ORANGE } : undefined}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="mt-5 grid gap-6 md:grid-cols-[1.6fr_1fr]">
        <div>
          <div className="rounded-2xl border border-[#cfe0bd] bg-[#fffaf3] px-5 py-4 text-[15px] leading-relaxed" style={{ color: INK }}>
            {tab === "desc" && (
              <>
                <p>{c.intro}</p>
                <ul className="mt-3 space-y-1.5">
                  {c.highlights.map((h) => (
                    <li key={h} className="flex gap-2">
                      <CheckCircle2 className="mt-0.5 size-4 shrink-0" style={{ color: GREEN }} /> {h}
                    </li>
                  ))}
                </ul>
              </>
            )}
            {tab === "usage" && (
              <ol className="space-y-3">
                {c.usage.map((u, i) => (
                  <li key={i} className="flex gap-3">
                    <span className="grid size-7 shrink-0 place-items-center rounded-full text-sm font-bold text-white" style={{ background: ORANGE }}>{i + 1}</span>
                    <span className="pt-0.5">{u}</span>
                  </li>
                ))}
              </ol>
            )}
            {tab === "reviews" && (
              <>
                <p className="flex items-center gap-2">
                  <span className="tnum text-2xl font-bold" style={{ color: GREEN }}>{avg.toFixed(1)}</span>
                  <Stars n={Math.round(avg)} /> <span className="text-sm text-[#6b5a4e]">trên {c.reviews.length} đánh giá</span>
                </p>
                <ul className="mt-3 divide-y divide-[#f0e2d2]">
                  {c.reviews.map((r, i) => (
                    <li key={i} className="py-3">
                      <div className="flex flex-wrap items-center gap-3">
                        <span className="font-semibold">{r.name}</span>
                        <Stars n={r.stars} />
                        <span className="text-sm text-[#6b5a4e]">{r.date}</span>
                      </div>
                      <p className="mt-1">{r.text}</p>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>

          <h3 className="mt-5 flex items-center gap-3 text-[22px] font-bold" style={{ color: INK }}>
            <PawPrint className="size-8 fill-current" style={{ color: ORANGE }} /> Thông tin nhanh
          </h3>
          <div className="mt-3 flex flex-wrap gap-2 sm:pl-8">
            {[`Mã sản phẩm: ${product.sku}`, `Đơn vị: ${product.unit}`, `Danh mục: ${product.categoryName}`].map((t) => (
              <span key={t} className="inline-flex items-center gap-1.5 rounded-full border border-[#f0d9bf] bg-[#fffaf3] px-3 py-1 text-[14px] font-medium" style={{ color: INK }}>
                <PawPrint className="size-4 fill-current" style={{ color: ORANGE }} /> {t}
              </span>
            ))}
          </div>
        </div>

        <div className="h-fit rounded-2xl p-5 text-white" style={{ background: GREEN }}>
          <p className="flex items-center gap-2 text-[20px] font-bold">
            <PawPrint className="size-6 fill-current" style={{ color: "#f7a964" }} /> Thông số
          </p>
          <ul className="mt-3 space-y-3 text-[14px]">
            {c.specs.map(([k, v], i) => {
              const Icon = specIcons[i] ?? Barcode;
              return (
                <li key={k} className="flex gap-3">
                  <Icon className="mt-0.5 size-4 shrink-0" />
                  <span><b className="font-semibold">{k}:</b> {v}</span>
                </li>
              );
            })}
          </ul>
        </div>
      </div>
    </section>
  );
}

function Stars({ n, className }: { n: number; className?: string }) {
  return (
    <span className={cn("flex", className)} aria-label={`${n} trên 5 sao`}>
      {Array.from({ length: 5 }, (_, i) => (
        <Star key={i} aria-hidden className={cn("size-4", i < n ? "fill-[#f5a623] text-[#f5a623]" : "text-[#d9cfc4]")} />
      ))}
    </span>
  );
}

function RelatedProducts({ product }: { product: Product }) {
  const related = useQuery({
    queryKey: ["products", { categoryId: product.categoryId, size: 5 }],
    queryFn: () => productApi.search({ categoryId: product.categoryId, size: 5 }),
  });
  const list = (related.data?.content ?? []).filter((p) => p.id !== product.id).slice(0, 4);
  if (list.length === 0) return null;
  const [species, group] = splitCategory(product.categoryName);

  return (
    <section className="relative overflow-hidden bg-[#a9cf98] pb-24 pt-10 md:pb-28">
      <Paw className="left-[6%] top-6 size-9 text-white/40" />
      <Paw className="right-[8%] top-10 size-10 text-white/40" />
      <Paw className="bottom-8 left-[2%] size-12 text-white/40" />
      <div className="mx-auto max-w-[1440px] px-4 sm:px-6">
        <div className="text-center">
          <h2 className="inline-flex items-center gap-3 rounded-full bg-[#fdf1e4] px-6 py-1.5 text-[24px] font-bold sm:text-[28px]" style={{ color: INK }}>
            <PawPrint className="size-7 fill-current" style={{ color: ORANGE }} /> Cùng nhóm {species.toLowerCase()} - {group.toLowerCase()}
          </h2>
          <p className="mt-2 text-[16px]" style={{ color: INK }}>Gợi ý sản phẩm tương tự cho bé {species.toLowerCase()} nhà bạn</p>
        </div>
        <ul className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4 lg:gap-6">
          {list.map((p, i) => (
            <li key={p.id}>
              <RelatedCard product={p} discount={[15, 10, 12, 15][i]} stars={[4, 4, 3, 3][i]} />
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function RelatedCard({ product, discount, stars }: { product: Product; discount: number; stars: number }) {
  const out = product.stockQuantity <= 0;
  return (
    <article className="relative flex h-full flex-col items-center rounded-2xl bg-[#fffaf3] p-4 text-center shadow-[0_8px_24px_-14px_rgba(0,0,0,0.35)]">
      {/* Nhãn giảm giá chỉ để trang trí (MOCK-TẠM): backend chưa có khuyến mãi. */}
      <span className="absolute left-3 top-3 rounded-full px-2.5 py-0.5 text-[14px] font-bold text-white" style={{ background: ORANGE }}>
        -{discount}%
      </span>
      <Link href={`/products/${product.id}`} className="block aspect-square w-[80%]">
        {product.imageUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={product.imageUrl} alt={product.name} loading="lazy" className="size-full object-contain" />
        )}
      </Link>
      <h3 className="mt-3 line-clamp-2 text-[16px] font-medium leading-snug" style={{ color: INK }}>
        <Link href={`/products/${product.id}`} className="hover:text-[#f26b1d]">{product.name}</Link>
      </h3>
      <p className="tnum mt-1 text-[16px] font-semibold" style={{ color: ORANGE }}>
        {formatPrice(product.price)} / {product.unit}
      </p>
      <p className="text-sm" style={{ color: INK }}>{out ? "Tạm hết hàng" : "Còn hàng"}</p>
      <Stars n={stars} className="mt-1" />
      <div className="mt-auto w-full pt-3">
        <AddToCartButton product={product} size="sm" className={cn(ORANGE_BTN, "w-full")} />
      </div>
    </article>
  );
}

function splitCategory(name: string): [string, string] {
  const [a, ...rest] = name.split(" - ");
  return [a.trim(), (rest.join(" - ") || a).trim()];
}

function DetailSkeleton() {
  return (
    <div className="bg-[#fdf1e4] py-10">
      <div className="mx-auto grid max-w-[1440px] gap-8 rounded-[32px] bg-[#fffaf3] p-8 md:grid-cols-2">
        <div className="aspect-square animate-pulse rounded-[28px] bg-[#fbd9b8]" />
        <div className="space-y-4 pt-4">
          <div className="h-6 w-1/2 animate-pulse rounded-full bg-[#fde4cc]" />
          <div className="h-10 w-3/4 animate-pulse rounded bg-[#fde4cc]" />
          <div className="h-10 w-1/3 animate-pulse rounded bg-[#fde4cc]" />
          <div className="h-24 w-full animate-pulse rounded bg-[#fde4cc]" />
        </div>
      </div>
    </div>
  );
}
