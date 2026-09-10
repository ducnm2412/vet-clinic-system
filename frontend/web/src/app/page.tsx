"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { homeFor } from "@/config/nav";
import { Spinner } from "@/components/ui";

/**
 * Cửa vào duy nhất: đưa mỗi người tới khu vực của vai trò mình, chưa đăng nhập thì tới
 * trang đăng nhập. Không có trang chủ chung vì bốn vai trò không chia sẻ màn hình nào.
 */
export default function RootPage() {
  const { ready, email, roles } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!ready) return;
    router.replace(email ? homeFor(roles) : "/login");
  }, [ready, email, roles, router]);

  return (
    <div className="flex min-h-dvh items-center justify-center">
      <Spinner label="Đang mở hệ thống" />
    </div>
  );
}
