"use client";

import { useQuery } from "@tanstack/react-query";
import { ApiError, staffApi } from "@/lib/api";
import { formatDate } from "@/lib/utils/format";
import type { StaffProfile } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  DataTable,
  EmptyState,
  ErrorState,
  TableFrame,
  TableSkeleton,
  type Column,
} from "@/components/ui";

/**
 * Danh sách nhân viên. `GET /profile/staff` chỉ ADMIN gọi được và trả hồ sơ nhân sự —
 * không kèm tên hay email vì những trường đó nằm ở auth-service, mà auth-service lại
 * chưa có endpoint đọc danh sách người dùng (VD-18).
 */
export default function AdminStaffPage() {
  const staff = useQuery({ queryKey: ["staff"], queryFn: staffApi.list });

  const columns: Column<StaffProfile>[] = [
    {
      key: "position",
      header: "Vị trí",
      cell: (s) => <span className="font-medium text-ink">{s.position || "Chưa ghi"}</span>,
    },
    { key: "phone", header: "Điện thoại", cell: (s) => s.phone || "—" },
    {
      key: "hire",
      header: "Ngày vào làm",
      hideBelow: "lg",
      cell: (s) => <span className="text-bark">{formatDate(s.hireDate)}</span>,
    },
  ];

  return (
    <>
      <PageHeader title="Nhân viên" description="Hồ sơ nhân sự của phòng khám." />

      <TableFrame title="Danh sách nhân viên" count={staff.data?.length}>
        {staff.isLoading ? (
          <TableSkeleton rows={4} cols={3} />
        ) : staff.isError ? (
          <div className="p-4">
            <ErrorState
              message={staff.error instanceof ApiError ? staff.error.message : "Không tải được danh sách."}
              onRetry={() => staff.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Nhân viên phòng khám"
            rows={staff.data ?? []}
            keyOf={(s) => s.id}
            columns={columns}
            empty={
              <EmptyState
                title="Chưa có hồ sơ nhân viên nào"
                description="Nhân viên tự điền hồ sơ sau khi được cấp tài khoản."
              />
            }
          />
        )}
      </TableFrame>
    </>
  );
}
