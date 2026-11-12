"use client";

import Link from "next/link";
import { Heart } from "lucide-react";
import type { Product } from "@/types";
import { formatPrice } from "@/lib/utils/format";
import { AddToCartButton, ProductImage } from "./ProductCard";

const ORANGE = "#f57c1f";

/** Thẻ sản phẩm kiểu cửa hàng: ảnh vuông, tim yêu thích, tên, danh mục, giá, nút thêm vào giỏ. Dùng ở /products và trang chủ. */
export function ShopCard({ product }: { product: Product }) {
  const out = product.stockQuantity <= 0;
  return (
    <article className="group relative flex h-full flex-col rounded-3xl bg-white p-4 shadow-[0_4px_24px_-10px_rgba(0,0,0,0.15)] transition hover:-translate-y-0.5 hover:shadow-[0_10px_30px_-10px_rgba(245,124,31,0.35)]">
      {product.lowStock && !out && (
        <span className="absolute left-4 top-4 z-10 rounded-md bg-[#f04e23] px-2 py-0.5 text-[11px] font-semibold text-white">
          Sắp hết
        </span>
      )}
      {out && (
        <span className="absolute left-4 top-4 z-10 rounded-md bg-neutral-400 px-2 py-0.5 text-[11px] font-semibold text-white">
          Hết hàng
        </span>
      )}
      <button aria-label="Yêu thích" className="absolute right-4 top-4 z-10 text-[#bbb] hover:text-[#f04e23]">
        <Heart className="size-6" strokeWidth={1.5} />
      </button>

      <Link href={`/products/${product.id}`} className="zoom-media block overflow-hidden rounded-xl">
        <ProductImage product={product} className="mx-auto aspect-square w-full !rounded-xl !bg-white [&_img]:object-contain" />
      </Link>

      <h3 className="mt-3 line-clamp-2 min-h-[2.6rem] text-[14px] font-medium leading-snug text-[#2b1d14]">
        <Link href={`/products/${product.id}`} className="hover:text-[#f57c1f]">{product.name}</Link>
      </h3>
      <p className="mt-1 text-xs text-[#9a8b80]">{product.categoryName}</p>

      <p className="mt-2 text-[17px] font-bold" style={{ color: ORANGE }}>
        {formatPrice(product.price)}
      </p>

      <div className="mt-auto pt-3">
        <AddToCartButton
          product={product}
          size="sm"
          className="!h-10 w-full !rounded-full !bg-gradient-to-r !from-[#ff8a2a] !to-[#f2661b] !text-white shadow-[0_6px_14px_-6px_rgba(245,124,31,0.8)] disabled:!from-neutral-300 disabled:!to-neutral-300"
        />
      </div>
    </article>
  );
}
