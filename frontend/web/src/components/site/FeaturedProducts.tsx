"use client";

import { useQuery } from "@tanstack/react-query";
import { productApi } from "@/lib/api";
import { ButtonLink, Section, SectionHead } from "./primitives";
import { ProductCard } from "./ProductCard";

/**
 * Sản phẩm nổi bật lấy thẳng từ product-service.
 *
 * Backend chưa có khái niệm "nổi bật": không có cờ featured, cũng không đếm lượt bán.
 * Nên phần dẫn chỉ nói đây là hàng bán tại phòng khám, không gọi là "bán chạy nhất"
 * trong khi chẳng ai đếm.
 */
export function FeaturedProducts() {
  const products = useQuery({
    queryKey: ["products", { size: 12 }],
    queryFn: () => productApi.search({ size: 12 }),
  });

  /*
    Lấy rộng rồi xếp món có ảnh lên trước. Phần lớn hàng trong kho chưa có ảnh, mà bốn mái
    vòm trống cạnh nhau ở ngay trang chủ thì nhìn như trang chưa tải xong. Đây là chuyện
    sắp xếp chỗ hiển thị, không đụng gì tới dữ liệu.
  */
  const list = [...(products.data?.content ?? [])]
    .sort((a, b) => Number(Boolean(b.imageUrl)) - Number(Boolean(a.imageUrl)))
    .slice(0, 4);

  if (products.isLoading) return <ProductSkeleton />;
  if (products.isError || list.length === 0) return null;

  return (
    <Section tone="peach">
      <SectionHead
        title="Đồ cho bé, mua tại phòng khám"
        lede="Thức ăn, phụ kiện và đồ chăm sóc chúng tôi dùng ngay tại đây. Hết hàng thì hiện hết hàng, không nhận đơn rồi hẹn."
        action={
          <ButtonLink href="/products" variant="outline">
            Xem tất cả sản phẩm
          </ButtonLink>
        }
      />

      <ul className="grid grid-cols-2 gap-x-6 gap-y-10 lg:grid-cols-4">
        {list.map((p) => (
          <li key={p.id}>
            <ProductCard product={p} />
          </li>
        ))}
      </ul>
    </Section>
  );
}

function ProductSkeleton() {
  return (
    <Section tone="peach">
      <div className="mb-12 h-10 w-80 animate-pulse rounded-full bg-white/70" />
      <ul className="grid grid-cols-2 gap-x-6 gap-y-10 lg:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <li key={i}>
            <div className="arch aspect-[4/5] w-full animate-pulse bg-white/70" />
            <div className="mt-4 space-y-2">
              <div className="h-4 w-1/2 animate-pulse rounded bg-white/70" />
              <div className="h-5 w-3/4 animate-pulse rounded bg-white/70" />
            </div>
          </li>
        ))}
      </ul>
    </Section>
  );
}
