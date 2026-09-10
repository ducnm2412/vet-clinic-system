"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { PetsLookupView } from "@/components/pet/PetsLookupView";

export default function Page() {
  return (
    <>
      <PageHeader title="Thú cưng" description="Tra hồ sơ bé trước khi khám." />
      <PetsLookupView />
    </>
  );
}
