"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { OrderManageView } from "@/components/order/OrderManageView";

export default function AdminOrdersPage() {
  return (
    <>
      <PageHeader
        title="Đơn hàng"
        description="Xác nhận, giao hàng và hoàn tất đơn của khách."
      />
      <OrderManageView />
    </>
  );
}
