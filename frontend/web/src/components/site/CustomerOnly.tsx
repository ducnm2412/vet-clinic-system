"use client";

import type { ReactNode } from "react";
import { RouteGuard } from "@/lib/auth";

/**
 * Những trang chỉ khách hàng mới vào được. Đây là lớp trải nghiệm — nó chỉ tránh cho
 * người ta đâm vào màn hình 401. Quyền thật vẫn do backend giữ, mọi endpoint đều tự
 * kiểm tra vai trò trên từng request.
 */
export function CustomerOnly({ children }: { children: ReactNode }) {
  return <RouteGuard allow={["CUSTOMER"]}>{children}</RouteGuard>;
}
