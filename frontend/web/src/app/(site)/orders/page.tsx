"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { PackageCheck } from "lucide-react";
import { orderApi } from "@/lib/api";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import type { OrderSummary } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { ORDER_LOOK, StatusPill } from "@/components/site/StatusPill";

const PAGE_SIZE = 10;

export default function OrdersPage() {
  return (
    <CustomerOnly>
      <OrdersBody />
    </CustomerOnly>
  );
}

function OrdersBody() {
  const [page, setPage] = useState(0);

  const orders = useQuery({
    queryKey: ["orders", "mine", page],
    queryFn: () => orderApi.mine(page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  const data = orders.data;
  const list = data?.content ?? [];

  return (
    <>
      <div className="bg-mint py-14 md:py-20">
        <Container>
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div>
              <h1 className="t-h2">Đơn hàng của bạn</h1>
              <p className="measure t-lede mt-4 text-stone">
                Theo dõi từ lúc đặt đến lúc nhận. Đơn nào đang đi đến đâu đều ghi rõ.
              </p>
            </div>
            <ButtonLink href="/products" variant="outline">
              Mua thêm
            </ButtonLink>
          </div>
        </Container>
      </div>

      <Container className="py-12 md:py-16">
        {orders.isLoading ? (
          <ul className="space-y-4" aria-hidden>
            {[0, 1, 2].map((i) => (
              <li key={i} className="h-28 animate-pulse rounded-[var(--radius-card)] bg-mint" />
            ))}
          </ul>
        ) : orders.isError ? (
          <div className="rounded-[var(--radius-card)] border border-mist px-6 py-14 text-center">
            <p className="t-h3">Chưa mở được danh sách đơn</p>
            <p className="mt-2 text-stone">Kết nối tới phòng khám đang trục trặc.</p>
            <SiteButton variant="outline" className="mt-6" onClick={() => orders.refetch()}>
              Thử lại
            </SiteButton>
          </div>
        ) : list.length === 0 ? (
          <div className="mx-auto max-w-lg py-10 text-center">
            <span
              aria-hidden
              className="arch mx-auto grid h-48 w-36 place-items-center bg-mint text-teal"
            >
              <PackageCheck className="size-14" strokeWidth={1.5} />
            </span>
            <h2 className="t-h2 mt-10">Bạn chưa đặt đơn nào</h2>
            <p className="mt-4 text-stone">
              Hạt, sữa tắm, vòng cổ và đồ chăm sóc đều có sẵn ở phòng khám.
            </p>
            <div className="mt-8">
              <ButtonLink href="/products" size="lg">
                Xem sản phẩm
              </ButtonLink>
            </div>
          </div>
        ) : (
          <>
            <ul className="space-y-5">
              {list.map((order) => (
                <li key={order.id}>
                  <OrderCard order={order} />
                </li>
              ))}
            </ul>

            {data && data.totalPages > 1 && (
              <nav
                aria-label="Phân trang đơn hàng"
                className="mt-12 flex items-center justify-center gap-2"
              >
                <SiteButton
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  Trang trước
                </SiteButton>
                <span className="tnum px-3 text-[15px] text-stone">
                  Trang {data.page + 1} trên {data.totalPages}
                </span>
                <SiteButton
                  variant="outline"
                  size="sm"
                  disabled={page >= data.totalPages - 1}
                  onClick={() => setPage(page + 1)}
                >
                  Trang sau
                </SiteButton>
              </nav>
            )}
          </>
        )}
      </Container>
    </>
  );
}

function OrderCard({ order }: { order: OrderSummary }) {
  return (
    <Link
      href={`/orders/${order.id}`}
      className="flex flex-wrap items-center gap-x-8 gap-y-4 rounded-[var(--radius-card)] border border-mist p-6 transition-[border-color,transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-teal hover:shadow-[var(--shadow-lift)]"
    >
      <div className="min-w-44">
        <p className="font-[family-name:var(--font-brand)] text-[19px] font-semibold">
          {order.orderCode}
        </p>
        <p className="mt-1 text-[15px] text-stone">Đặt lúc {formatDateTime(order.createdAt)}</p>
      </div>

      <p className="tnum min-w-24 text-stone">{order.totalItems} món</p>

      <p className="tnum min-w-32 text-[19px] font-semibold">{formatPrice(order.total)}</p>

      <div className="ml-auto">
        <StatusPill look={ORDER_LOOK[order.status]} />
      </div>
    </Link>
  );
}
