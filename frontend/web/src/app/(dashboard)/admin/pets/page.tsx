"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { PetsLookupView } from "@/components/pet/PetsLookupView";

export default function Page() {
  return (
    <>
      <PageHeader title="Thú cưng" description="Toàn bộ thú cưng khách đã đăng ký." />
      <PetsLookupView />
    </>
  );
}
