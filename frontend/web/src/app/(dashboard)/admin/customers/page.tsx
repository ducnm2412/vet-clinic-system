"use client";

import { MISSING } from "@/lib/api";
import { PageHeader } from "@/components/layout/DashboardShell";
import { PendingApi } from "@/components/ui";

export default function Page() {
  return (
    <>
      <PageHeader title="Khách hàng" description="Danh sách khách và hồ sơ liên hệ." />
      <PendingApi endpoint={MISSING.customerList} />
    </>
  );
}
