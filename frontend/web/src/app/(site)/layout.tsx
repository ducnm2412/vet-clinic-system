import type { ReactNode } from "react";
import { SiteHeader } from "@/components/site/SiteHeader";
import { SiteFooter } from "@/components/site/SiteFooter";

/**
 * Khung của website khách hàng: đầu trang thương mại, không phải thanh bên quản trị.
 * Dashboard của bác sĩ, nhân viên và quản trị vẫn nằm ở nhóm (dashboard) với khung riêng.
 */
export default function SiteLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-white text-pine">
      <SiteHeader />
      <main className="flex-1">{children}</main>
      <SiteFooter />
    </div>
  );
}
