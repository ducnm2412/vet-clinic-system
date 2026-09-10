"use client";

import { MISSING } from "@/lib/api";
import { PageHeader } from "@/components/layout/DashboardShell";
import { PendingApi } from "@/components/ui";

export default function Page() {
  return (
    <>
      <PageHeader title="Báo cáo" description="Doanh thu, lịch khám và tồn kho theo thời gian." />
      <PendingApi endpoint={MISSING.revenueSeries} />
    </>
  );
}
