"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ApiError, orderApi } from "@/lib/api";
import { ORDER_STATUS } from "@/lib/utils/status";
import { formatDateTime, formatPrice } from "@/lib/utils/format";
import type { OrderSummary } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  Pagination,
  StatusTag,
  TableFrame,
  TableSkeleton,
  type Column,
} from "@/components/ui";

export default function CustomerOrdersPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);

  const orders = useQuery({
    queryKey: ["orders", "mine", { page }],
    queryFn: () => orderApi.mine(page, 20),
  });

  const columns: Column<OrderSummary>[] = [
    {
      key: "code",
      header: "Mã đơn",
      cell: (o) => <span className="font-medium text-ink">{o.orderCode}</span>,
    },
    {
      key: "created",
      header: "Đặt lúc",
      cell: (o) => <span className="text-bark">{formatDateTime(o.createdAt)}</span>,
    },
    { key: "items", header: "Số món", numeric: true, hideBelow: "lg", cell: (o) => o.totalItems },
    { key: "status", header: "Trạng thái", cell: (o) => <StatusTag status={ORDER_STATUS[o.status]} /> },
    { key: "total", header: "Tổng tiền", numeric: true, cell: (o) => formatPrice(o.total) },
  ];

  return (
    <>
      <PageHeader title="Đơn hàng của tôi" description="Theo dõi đơn từ lúc đặt tới khi nhận." />

      <TableFrame title="Đơn hàng">
        {orders.isLoading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : orders.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                orders.error instanceof ApiError ? orders.error.message : "Không tải được đơn hàng."
              }
              onRetry={() => orders.refetch()}
            />
          </div>
        ) : (
          <>
            <DataTable
              caption="Đơn hàng của khách"
              rows={orders.data?.content ?? []}
              keyOf={(o) => o.id}
              columns={columns}
              onRowClick={(o) => router.push(`/customer/orders/${o.id}`)}
              empty={
                <EmptyState
                  title="Bạn chưa đặt đơn nào"
                  description="Ghé cửa hàng xem có gì hợp với bé nhà bạn."
                  action={
                    <Link href="/customer/shop">
                      <Button size="sm">Xem sản phẩm</Button>
                    </Link>
                  }
                />
              }
            />
            {orders.data && (
              <Pagination
                page={orders.data.page}
                totalPages={orders.data.totalPages}
                totalElements={orders.data.totalElements}
                unitLabel="đơn hàng"
                onChange={setPage}
              />
            )}
          </>
        )}
      </TableFrame>
    </>
  );
}
