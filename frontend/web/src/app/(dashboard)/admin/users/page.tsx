"use client";

import { MISSING } from "@/lib/api";
import { PageHeader } from "@/components/layout/DashboardShell";
import { PendingApi } from "@/components/ui";

export default function Page() {
  return (
    <>
      <PageHeader title="Tài khoản" description="Tạo và xoá tài khoản Bác sĩ, Nhân viên." />
      <PendingApi endpoint={MISSING.userList} />
    </>
  );
}
