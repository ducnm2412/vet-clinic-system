import type { ReactNode } from "react";
import { RouteGuard } from "@/lib/auth";
import { DashboardShell } from "@/components/layout/DashboardShell";

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <RouteGuard allow={["CUSTOMER"]}>
      <DashboardShell role="CUSTOMER">{children}</DashboardShell>
    </RouteGuard>
  );
}
