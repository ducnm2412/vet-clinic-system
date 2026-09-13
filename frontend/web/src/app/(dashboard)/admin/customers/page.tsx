"use client";

import { PageHeader } from "@/components/layout/DashboardShell";
import { UserTable } from "@/components/admin/UserTable";

/**
 * Danh sách khách hàng = tài khoản vai trò CUSTOMER ở auth-service. Số điện thoại, địa chỉ và
 * thú cưng nằm ở profile-service, chưa có endpoint liệt kê nên chưa ghép vào đây.
 */
export default function Page() {
  return (
    <>
      <PageHeader title="Khách hàng" description="Khách đã đăng ký tài khoản trên website." />
      <UserTable role="CUSTOMER" title="Danh sách khách hàng" unitLabel="khách hàng" />
    </>
  );
}
