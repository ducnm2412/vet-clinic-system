"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Boxes, CalendarDays, PackageSearch, Receipt } from "lucide-react";
import { MISSING, bookingApi, orderManageApi, productApi } from "@/lib/api";
import { APPOINTMENT_STATUS, ORDER_STATUS } from "@/lib/utils/status";
import { formatDateTime, formatPrice, formatTime, todayISO } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Metric, MetricRow } from "@/components/dashboard/Metric";
import {
  DataTable,
  EmptyState,
  ErrorState,
  PendingApi,
  StatusTag,
  TableFrame,
  TableSkeleton,
  type Column,
} from "@/components/ui";
import type { OrderSummary, Product, Appointment } from "@/types";

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
              columns={APPOINTMENT_COLUMNS}
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
              columns={ORDER_COLUMNS}
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

const APPOINTMENT_COLUMNS: Column<Appointment>[] = [
  { key: "time", header: "Giờ", cell: (a) => formatTime(a.startTime) },
  {
    key: "status",
    header: "Trạng thái",
    cell: (a) => <StatusTag status={APPOINTMENT_STATUS[a.status]} />,
  },
  {
    key: "reason",
    header: "Lý do khám",
    hideBelow: "lg",
    cell: (a) => <span className="text-ink-soft">{a.reason || "Không ghi"}</span>,
  },
];

const ORDER_COLUMNS: Column<OrderSummary>[] = [
  { key: "code", header: "Mã đơn", cell: (o) => o.orderCode },
  { key: "status", header: "Trạng thái", cell: (o) => <StatusTag status={ORDER_STATUS[o.status]} /> },
  {
    key: "created",
    header: "Đặt lúc",
    hideBelow: "lg",
    cell: (o) => <span className="text-bark">{formatDateTime(o.createdAt)}</span>,
  },
  { key: "total", header: "Tổng tiền", numeric: true, cell: (o) => formatPrice(o.total) },
];

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
