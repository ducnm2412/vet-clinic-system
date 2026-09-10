"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ShoppingBag } from "lucide-react";
import { ApiError, cartApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatPrice } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { useToast } from "@/components/ui";
import type { Product } from "@/types";
import { ArchPlaceholder, SiteButton } from "./primitives";

/** Số bất kỳ nhưng ổn định từ một chuỗi, để cùng một sản phẩm luôn ra cùng màu vòm. */
function hash(text: string): number {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) | 0;
  return h;
}

export function ProductCard({ product }: { product: Product }) {
  const out = product.stockQuantity <= 0;

  return (
    <article className="group flex flex-col">
      <Link
        href={`/products/${product.id}`}
        className="block overflow-hidden rounded-[999px_999px_var(--radius-card)_var(--radius-card)]"
      >
        <ProductImage product={product} className="aspect-[4/5] w-full" />
      </Link>

      <div className="flex min-h-0 flex-1 flex-col px-1 pt-4">
        <p className="text-sm text-stone">{product.categoryName}</p>
        <h3 className="mt-1 font-[family-name:var(--font-brand)] text-[19px] font-semibold leading-snug text-pine">
          <Link href={`/products/${product.id}`} className="hover:text-teal-deep">
            {product.name}
          </Link>
        </h3>

        <div className="mt-auto pt-3">
          <p className="tnum text-[19px] font-semibold text-pine">
            {formatPrice(product.price)}
            <span className="ml-1 text-sm font-normal text-stone">/ {product.unit}</span>
          </p>
          <StockLine product={product} />
          <div className="mt-3">
            <AddToCartButton product={product} size="sm" className={cn(out && "hidden")} />
          </div>
        </div>
      </div>
    </article>
  );
}

/**
 * Ảnh sản phẩm. Rất nhiều món trong kho chưa có `imageUrl`, nên phải có chỗ đứng tử tế:
 * mái vòm màu kèm dấu chân, trông vẫn cố ý chứ không như ảnh vỡ.
 * Dùng thẻ img thường chứ không phải next/image vì đường dẫn ảnh do người nhập hàng gõ
 * vào, không biết trước tên miền để khai trong remotePatterns.
 */
export function ProductImage({
  product,
  className,
}: {
  product: Product;
  className?: string;
}) {
  if (!product.imageUrl) {
    return <ArchPlaceholder tone={hash(product.categoryName || product.sku)} className={className} />;
  }
  return (
    <div className={cn("arch bg-mint", className)}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={product.imageUrl}
        alt={product.name}
        loading="lazy"
        className="size-full object-cover transition-transform duration-500 [transition-timing-function:var(--ease)] group-hover:scale-[1.03]"
      />
    </div>
  );
}

export function StockLine({ product }: { product: Product }) {
  if (product.stockQuantity <= 0) {
    return <p className="mt-1 text-sm text-coral-deep">Tạm hết hàng</p>;
  }
  if (product.lowStock) {
    return <p className="tnum mt-1 text-sm text-stone">Chỉ còn {product.stockQuantity} phần</p>;
  }
  return <p className="mt-1 text-sm text-stone">Còn hàng</p>;
}

/**
 * Thêm vào giỏ. Backend chỉ mở /cart cho CUSTOMER, nên người chưa đăng nhập được đưa
 * sang trang đăng nhập kèm đường quay lại, thay vì bấm xong nhận 401 không hiểu vì sao.
 */
export function AddToCartButton({
  product,
  quantity = 1,
  size = "md",
  className,
}: {
  product: Product;
  quantity?: number;
  size?: "sm" | "md" | "lg";
  className?: string;
}) {
  const { email, hasRole } = useAuth();
  const router = useRouter();
  const qc = useQueryClient();
  const toast = useToast();

  const add = useMutation({
    mutationFn: () => cartApi.addItem({ productId: product.id, quantity }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cart"] });
      toast.success(`Đã thêm ${product.name} vào giỏ`);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không thêm được vào giỏ."),
  });

  const out = product.stockQuantity <= 0;

  if (email && !hasRole("CUSTOMER")) {
    return (
      <p className={cn("text-sm text-stone", className)}>
        Mua hàng cần tài khoản khách hàng.
      </p>
    );
  }

  return (
    <SiteButton
      size={size}
      variant="primary"
      className={className}
      disabled={out}
      loading={add.isPending}
      onClick={() => {
        if (!email) {
          router.push(`/login?next=${encodeURIComponent(`/products/${product.id}`)}`);
          return;
        }
        add.mutate();
      }}
    >
      <ShoppingBag aria-hidden className="size-4" />
      {out ? "Tạm hết hàng" : "Thêm vào giỏ"}
    </SiteButton>
  );
}
