"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { InventoryView, LowStockBanner } from "@/components/inventory/InventoryView";

export default function AdminInventoryPage() {
  return (
    <>
      <PageHeader
        title="Tồn kho"
        description="Nhập hàng, điều chỉnh sau kiểm kê và tra lịch sử biến động."
      />
      <LowStockBanner />
      <InventoryView />
    </>
  );
}
