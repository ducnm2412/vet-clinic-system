"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { OrderManageView } from "@/components/order/OrderManageView";

export default function StaffOrdersPage() {
  return (
    <>
      <PageHeader
        title="Đơn hàng"
        description="Xác nhận đơn mới rồi chuyển sang giao hàng."
      />
      <OrderManageView />
    </>
  );
}
