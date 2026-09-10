"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Boxes, CalendarDays, CreditCard, Receipt } from "lucide-react";
import { MISSING, bookingApi, orderManageApi, paymentApi, productApi } from "@/lib/api";
import { PAYMENT_STATUS } from "@/lib/utils/status";
import { formatPrice, todayISO } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Metric, MetricRow } from "@/components/dashboard/Metric";
import { orderColumns } from "@/components/dashboard/columns";
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
import type { Payment } from "@/types";

export default function StaffDashboard() {
  const today = todayISO();

  const payments = useQuery({ queryKey: ["payments", "pending"], queryFn: paymentApi.pending });
  const orders = useQuery({
    queryKey: ["orders", "manage", { status: "PENDING" }],
    queryFn: () => orderManageApi.list({ status: "PENDING", size: 8 }),
  });
  const appointments = useQuery({
    queryKey: ["appointments", { date: today }],
    queryFn: () => bookingApi.search({ date: today }),
  });
  const lowStock = useQuery({ queryKey: ["products", "low-stock"], queryFn: productApi.lowStock });

  return (
    <>
      <PageHeader title="Tổng quan" description="Việc ở quầy và trong kho đang chờ bạn." />

      <MetricRow>
        <Metric
          label="Phiếu thuốc chờ thu"
          value={payments.data?.length ?? null}
          icon={CreditCard}
          loading={payments.isLoading}
          tone={payments.data?.length ? "alert" : "plain"}
        />
        <Metric
          label="Đơn chờ xác nhận"
          value={orders.data?.totalElements ?? null}
          icon={Receipt}
          loading={orders.isLoading}
          tone={orders.data?.totalElements ? "alert" : "plain"}
        />
        <Metric
          label="Lịch khám hôm nay"
          value={appointments.data?.length ?? null}
          icon={CalendarDays}
          loading={appointments.isLoading}
        />
        <Metric
          label="Hàng sắp hết"
          value={lowStock.data?.length ?? null}
          icon={Boxes}
          loading={lowStock.isLoading}
          tone={lowStock.data?.length ? "alert" : "plain"}
        />
      </MetricRow>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        <TableFrame
          title="Phiếu thuốc chờ thu tiền"
          count={payments.data?.length}
          actions={
            <Link href="/staff/payments" className="text-sm text-moss underline underline-offset-2">
              Mở trang thu tiền
            </Link>
          }
        >
          {payments.isLoading ? (
            <TableSkeleton rows={4} cols={3} />
          ) : payments.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được phiếu thu." onRetry={() => payments.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Phiếu thuốc chờ thu tiền"
              rows={payments.data ?? []}
              keyOf={(p) => p.id}
              columns={PAYMENT_COLUMNS}
              empty={<EmptyState title="Không còn phiếu nào chờ thu" />}
            />
          )}
        </TableFrame>

        <TableFrame
          title="Đơn chờ xác nhận"
          count={orders.data?.totalElements}
          actions={
            <Link href="/staff/orders" className="text-sm text-moss underline underline-offset-2">
              Mở trang đơn hàng
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
              caption="Đơn hàng chờ xác nhận"
              rows={orders.data?.content ?? []}
              keyOf={(o) => o.id}
              columns={orderColumns({ showRecipient: true })}
              empty={<EmptyState title="Không có đơn nào chờ xác nhận" />}
            />
          )}
        </TableFrame>
      </div>

      <div className="mt-6">
        <h2 className="mb-2 font-medium text-ink">Chấm công</h2>
        <PendingApi endpoint={MISSING.attendance} />
      </div>
    </>
  );
}

const PAYMENT_COLUMNS: Column<Payment>[] = [
  {
    key: "status",
    header: "Trạng thái",
    cell: (p) => <StatusTag status={PAYMENT_STATUS[p.status]} />,
  },
  { key: "items", header: "Số thuốc", numeric: true, cell: (p) => p.items.length },
  {
    key: "amount",
    header: "Số tiền",
    numeric: true,
    cell: (p) =>
      p.amount === null ? <span className="text-bark">Chưa nhập</span> : formatPrice(p.amount),
  },
];

