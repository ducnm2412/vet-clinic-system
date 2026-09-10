"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "./context";
import type { Role } from "@/types";

/**
 * Chặn route ở phía giao diện. Đây là lớp trải nghiệm, không phải lớp bảo mật —
 * backend vẫn kiểm quyền trên từng request, và mọi endpoint đều trả 401/403 độc lập.
 */
export function RouteGuard({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const { ready, roles, email } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const allowed = roles.some((r) => allow.includes(r));

  useEffect(() => {
    if (!ready) return;
    if (!email) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    } else if (!allowed) {
      router.replace("/unauthorized");
    }
  }, [ready, email, allowed, router, pathname]);

  // Chưa đọc xong token, hoặc đang chuyển hướng — không vẽ nội dung của role khác.
  if (!ready || !email || !allowed) {
    return (
      <div className="flex min-h-64 items-center justify-center">
        <span
          aria-label="Đang kiểm tra quyền truy cập"
          role="status"
          className="size-5 animate-spin rounded-full border-2 border-moss border-t-transparent"
        />
      </div>
    );
  }

  return <>{children}</>;
}
