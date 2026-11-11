import type { ReactNode } from "react";
import { UserLayout } from "@/components/user/UserLayout";

/** Mọi trang /user/* dùng chung sidebar khách hàng. */
export default function Layout({ children }: { children: ReactNode }) {
  return <UserLayout>{children}</UserLayout>;
}
