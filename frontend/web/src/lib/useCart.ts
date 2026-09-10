"use client";

import { useQuery } from "@tanstack/react-query";
import { cartApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";

/**
 * Giỏ hàng dùng chung cho đầu trang và trang giỏ. Backend chỉ mở /cart cho CUSTOMER, nên
 * khách chưa đăng nhập hoặc người đăng nhập bằng tài khoản nội bộ sẽ không gọi — gọi vào
 * chỉ nhận 401/403 rồi bày lỗi vô cớ ra đầu trang.
 */
export function useCart() {
  const { email, hasRole } = useAuth();
  const enabled = Boolean(email) && hasRole("CUSTOMER");

  const query = useQuery({
    queryKey: ["cart"],
    queryFn: cartApi.get,
    enabled,
  });

  return {
    ...query,
    /** Người chưa đăng nhập vẫn xem được cửa hàng, chỉ không có giỏ. */
    enabled,
    itemCount: query.data?.totalItems ?? 0,
  };
}
