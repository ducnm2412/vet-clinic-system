"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { PackageCheck, ShoppingBag } from "lucide-react";
import { orderApi } from "@/lib/api";
import type { OrderStatus } from "@/types";
import { OrderCard } from "@/components/user/Cards";
import { Pagination, SearchInput } from "@/components/user/Controls";
import { EmptyState, ErrorState, SkeletonList } from "@/components/user/EmptyState";
import { PageHeader } from "@/components/user/PageHeader";
import { Tabs } from "@/components/user/Tabs";
import { btnPrimary } from "@/components/user/ui";

const PAGE_SIZE = 10;

type Tab = "ALL" | OrderStatus;
const TABS: Array<{ key: Tab; label: string }> = [
  { key: "ALL", label: "Tất cả" },
  { key: "PENDING", label: "Chờ xác nhận" },
  { key: "CONFIRMED", label: "Đang xử lý" },
  { key: "SHIPPING", label: "Đang giao" },
  { key: "COMPLETED", label: "Hoàn thành" },
  { key: "CANCELLED", label: "Đã huỷ" },
];

export default function MyOrdersPage() {
  const [page, setPage] = useState(0);
  const [tab, setTab] = useState<Tab>("ALL");
  const [q, setQ] = useState("");

  const orders = useQuery({
    queryKey: ["orders", "mine", page],
    queryFn: () => orderApi.mine(page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  const data = orders.data;
  const all = data?.content ?? [];
  // API "đơn của tôi" chưa lọc theo trạng thái, nên lọc và đếm trên trang đang xem.
  const list = all
    .filter((o) => tab === "ALL" || o.status === tab)
    .filter((o) => o.orderCode.toLowerCase().includes(q.trim().toLowerCase()));

  return (
    <>
      <PageHeader
        title="Đơn hàng của tôi"
        crumbs={[{ label: "Đơn hàng của tôi" }]}
        action={
          <Link href="/products" className={btnPrimary}>
            <ShoppingBag aria-hidden className="size-4" /> Mua sắm
          </Link>
        }
      />

      <div className="mb-5 flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
        <Tabs<Tab>
          tabs={TABS.map((t) => ({
            ...t,
            count: t.key === "ALL" ? data?.totalElements ?? 0 : all.filter((o) => o.status === t.key).length,
          }))}
          value={tab}
          onChange={setTab}
        />
        <SearchInput value={q} onChange={setQ} placeholder="Tìm theo mã đơn…" className="xl:w-56" />
      </div>

      {orders.isLoading ? (
        <SkeletonList count={4} />
      ) : orders.isError ? (
        <ErrorState title="Chưa mở được danh sách đơn" onRetry={() => orders.refetch()} />
      ) : list.length === 0 ? (
        <EmptyState
          icon={PackageCheck}
          title={all.length === 0 ? "Bạn chưa đặt đơn nào" : "Không có đơn nào ở mục này"}
          description={all.length === 0 ? "Hạt, sữa tắm, vòng cổ và đồ chăm sóc đều có sẵn tại cửa hàng." : undefined}
          action={
            all.length === 0 ? (
              <Link href="/products" className={btnPrimary}>
                Xem sản phẩm
              </Link>
            ) : undefined
          }
        />
      ) : (
        <ul className="space-y-3">
          {list.map((order) => (
            <li key={order.id}>
              <OrderCard order={order} />
            </li>
          ))}
        </ul>
      )}

      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
