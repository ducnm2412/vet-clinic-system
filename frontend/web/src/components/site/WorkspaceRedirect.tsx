"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { homeFor } from "@/config/nav";
import { consumeInitialLoadAt } from "@/lib/initialPath";

/**
 * Bác sĩ, nhân viên, admin đã đăng nhập mà mở lại web (gõ địa chỉ, mở tab mới, F5) ở trang chủ
 * thì đưa thẳng vào khu làm việc của mình. Chỉ áp dụng cho lần tải trang đầu tiên: họ vẫn xem
 * được trang chủ khi bấm "Trang chủ" trên website, hoặc mở bằng nút "Mở website khách hàng"
 * trong dashboard (nút đó gắn `?xem=website` nên không khớp "/").
 */
export function WorkspaceRedirect() {
  const { ready, roles } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!ready) return;
    const home = homeFor(roles);
    // Phải gọi consume dù là khách hay chưa đăng nhập, để lần quay lại "/" sau đó không bị chuyển.
    const firstLoadAtHome = consumeInitialLoadAt("/");
    if (home !== "/" && firstLoadAtHome) router.replace(home);
  }, [ready, roles, router]);

  return null;
}
