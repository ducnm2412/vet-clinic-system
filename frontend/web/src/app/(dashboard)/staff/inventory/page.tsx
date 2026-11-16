"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { InventoryTabs } from "@/components/inventory/InventoryTabs";

export default function StaffInventoryPage() {
  return (
    <>
      <PageHeader
        title="Tồn kho"
        description="Kho sản phẩm và kho thuốc: nhập hàng về và điều chỉnh tồn sau khi kiểm kê."
      />
      <InventoryTabs />
    </>
  );
}
