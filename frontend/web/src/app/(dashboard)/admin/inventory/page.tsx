"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { InventoryTabs } from "@/components/inventory/InventoryTabs";

export default function AdminInventoryPage() {
  return (
    <>
      <PageHeader
        title="Tồn kho"
        description="Kho sản phẩm và kho thuốc: nhập hàng, điều chỉnh sau kiểm kê và tra lịch sử biến động."
      />
      <InventoryTabs />
    </>
  );
}
