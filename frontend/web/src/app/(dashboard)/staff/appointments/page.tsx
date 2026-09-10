"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { AppointmentsView } from "@/components/appointment/AppointmentsView";

export default function Page() {
  return (
    <>
      <PageHeader title="Lịch khám" description="Xác nhận lịch khách đặt và cập nhật sau khi khám." />
      <AppointmentsView />
    </>
  );
}
