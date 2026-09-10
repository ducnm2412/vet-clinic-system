"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Boxes, CalendarDays, PackageSearch, Receipt } from "lucide-react";
import { MISSING, bookingApi, orderManageApi, productApi } from "@/lib/api";
import { todayISO } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Metric, MetricRow } from "@/components/dashboard/Metric";
import { appointmentColumns, orderColumns } from "@/components/dashboard/columns";
import {
  DataTable,
  EmptyState,
  ErrorState,
  PendingApi,
  TableFrame,
  TableSkeleton,
  type Column,
} from "@/components/ui";
import type { Product } from "@/types";

export default function AdminDashboard() {
  const today = todayISO();

  const lowStock = useQuery({ queryKey: ["products", "low-stock"], queryFn: productApi.lowStock });
  const orders = useQuery({
    queryKey: ["orders", "manage", { page: 0 }],
    queryFn: () => orderManageApi.list({ page: 0, size: 8 }),
  });
  const appointments = useQuery({
    queryKey: ["appointments", { date: today }],
    queryFn: () => bookingApi.search({ date: today }),
  });

  const pendingOrders = orders.data?.content.filter((o) => o.status === "PENDING").length ?? null;

  return (
    <>
      <PageHeader
        title="Tổng quan"
        description="Những việc đang chờ xử lý trong hôm nay."
      />

      <MetricRow>
        <Metric
          label="Lịch khám hôm nay"
          value={appointments.data?.length ?? null}
          icon={CalendarDays}
          loading={appointments.isLoading}
        />
        <Metric
          label="Đơn chờ xác nhận"
          value={pendingOrders}
          icon={Receipt}
          loading={orders.isLoading}
          tone={pendingOrders ? "alert" : "plain"}
        />
        <Metric
          label="Sản phẩm sắp hết"
          value={lowStock.data?.length ?? null}
          icon={Boxes}
          loading={lowStock.isLoading}
          tone={lowStock.data?.length ? "alert" : "plain"}
        />
        <Metric
          label="Tổng đơn hàng"
          value={orders.data?.totalElements ?? null}
          icon={PackageSearch}
          loading={orders.isLoading}
        />
      </MetricRow>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        <TableFrame
          title="Lịch khám hôm nay"
          count={appointments.data?.length}
          actions={
            <Link href="/admin/appointments" className="text-sm text-moss underline underline-offset-2">
              Xem tất cả
            </Link>
          }
        >
          {appointments.isLoading ? (
            <TableSkeleton rows={4} cols={3} />
          ) : appointments.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được lịch khám." onRetry={() => appointments.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Lịch khám trong ngày"
              rows={appointments.data ?? []}
              keyOf={(a) => a.id}
              columns={appointmentColumns()}
              empty={<EmptyState title="Hôm nay chưa có lịch khám nào" />}
            />
          )}
        </TableFrame>

        <TableFrame
          title="Đơn hàng gần đây"
          count={orders.data?.totalElements}
          actions={
            <Link href="/admin/orders" className="text-sm text-moss underline underline-offset-2">
              Xem tất cả
            </Link>
          }
        >
          {orders.isLoading ? (
            <TableSkeleton rows={4} cols={3} />
          ) : orders.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được đơn hàng." onRetry={() => orders.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Đơn hàng mới nhất"
              rows={orders.data?.content ?? []}
              keyOf={(o) => o.id}
              columns={orderColumns({ showCreated: true })}
              empty={<EmptyState title="Chưa có đơn hàng nào" />}
            />
          )}
        </TableFrame>
      </div>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        <TableFrame title="Hàng sắp hết" count={lowStock.data?.length}>
          {lowStock.isLoading ? (
            <TableSkeleton rows={3} cols={3} />
          ) : lowStock.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được danh sách tồn kho." onRetry={() => lowStock.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Sản phẩm dưới ngưỡng tồn"
              rows={lowStock.data ?? []}
              keyOf={(p) => p.id}
              columns={LOW_STOCK_COLUMNS}
              empty={<EmptyState title="Mọi mặt hàng đều còn đủ" />}
            />
          )}
        </TableFrame>

        <div>
          <h2 className="mb-2 font-medium text-ink">Doanh thu</h2>
          <PendingApi endpoint={MISSING.revenueSeries} />
        </div>
      </div>
    </>
  );
}



const LOW_STOCK_COLUMNS: Column<Product>[] = [
  { key: "name", header: "Sản phẩm", cell: (p) => p.name },
  { key: "sku", header: "Mã hàng", hideBelow: "lg", cell: (p) => <span className="text-bark">{p.sku}</span> },
  {
    key: "stock",
    header: "Còn lại",
    numeric: true,
    cell: (p) => (
      <span className={p.stockQuantity <= 0 ? "text-danger" : "text-ink"}>
        {p.stockQuantity} {p.unit}
      </span>
    ),
  },
];
