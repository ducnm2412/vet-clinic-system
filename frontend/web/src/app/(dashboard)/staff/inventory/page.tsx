"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { InventoryView, LowStockBanner } from "@/components/inventory/InventoryView";

export default function StaffInventoryPage() {
  return (
    <>
      <PageHeader
        title="Tồn kho"
        description="Nhập hàng về và điều chỉnh tồn sau khi kiểm kê."
      />
      <LowStockBanner />
      <InventoryView />
    </>
  );
}
