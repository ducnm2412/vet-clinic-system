"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { AdminAppointmentBoard } from "@/components/appointment/AdminAppointmentBoard";

export default function Page() {
  return (
    <>
      <PageHeader
        title="Lịch khám"
        description="Xếp bác sĩ cho lịch khách đã đặt, đề xuất giờ khác khi không ai rảnh, và theo dõi lịch trong ngày."
      />
      <AdminAppointmentBoard />
    </>
  );
}
