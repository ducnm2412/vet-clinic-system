"use client";

import { useQuery } from "@tanstack/react-query";
import { ApiError, authApi, staffApi } from "@/lib/api";
import { formatDate } from "@/lib/utils/format";
import { USER_STATUS } from "@/lib/utils/status";
import type { CurrentUser, StaffProfile } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  DataTable,
  EmptyState,
  ErrorState,
  StatusTag,
  TableFrame,
  TableSkeleton,
  type Column,
} from "@/components/ui";

type StaffRow = { user: CurrentUser; profile: StaffProfile | undefined };

/**
 * Danh sách nhân viên = tài khoản vai trò STAFF (auth-service, có họ tên và email), ghép với
 * hồ sơ nhân sự (profile-service, có vị trí, điện thoại, ngày vào làm) theo userId.
 *
 * Đi từ tài khoản chứ không từ hồ sơ: hồ sơ chỉ sinh ra khi người đó mở trang hồ sơ lần đầu,
 * nên nhân viên mới chưa có; và admin mở trang đó cũng sinh ra một hồ sơ "nhân viên".
 */
export default function AdminStaffPage() {
  // Phòng khám có vài chục nhân viên — một trang 100 là đủ, backend không cho lớn hơn.
  const users = useQuery({
    queryKey: ["admin-users", { role: "STAFF", size: 100 }],
    queryFn: () => authApi.listUsers({ role: "STAFF", size: 100 }),
  });
  const profiles = useQuery({ queryKey: ["staff"], queryFn: staffApi.list });

  const byUserId = new Map((profiles.data ?? []).map((p) => [p.userId, p]));
  const rows: StaffRow[] = (users.data?.content ?? []).map((user) => ({ user, profile: byUserId.get(user.id) }));

  const columns: Column<StaffRow>[] = [
    {
      key: "name",
      header: "Họ tên",
      cell: ({ user }) => (
        <span>
          <span className="block font-medium text-ink">{`${user.firstName} ${user.lastName}`.trim()}</span>
          <span className="block break-all text-sm text-bark">{user.email}</span>
        </span>
      ),
    },
    {
      key: "position",
      header: "Vị trí",
      cell: ({ profile }) =>
        profile?.position ? profile.position : <span className="text-bark">{profile ? "Chưa ghi" : "Chưa điền hồ sơ"}</span>,
    },
    { key: "phone", header: "Điện thoại", hideBelow: "md", cell: ({ profile }) => profile?.phone || "—" },
    {
      key: "hire",
      header: "Ngày vào làm",
      hideBelow: "lg",
      cell: ({ profile }) => <span className="text-bark tnum">{formatDate(profile?.hireDate)}</span>,
    },
    { key: "status", header: "Trạng thái", cell: ({ user }) => <StatusTag status={USER_STATUS[user.status]} /> },
  ];

  const loading = users.isLoading || profiles.isLoading;
  // Thiếu hồ sơ thì vẫn hiện được tên và email; thiếu danh sách tài khoản thì không có gì để hiện.
  const failed = users.isError ? users : null;

  return (
    <>
      <PageHeader title="Nhân viên" description="Nhân viên quầy của phòng khám và hồ sơ nhân sự của họ." />

      {profiles.isError && !users.isError && (
        <ErrorState
          className="mb-4"
          message="Không tải được hồ sơ nhân sự — đang hiện tên và email, thiếu vị trí và điện thoại."
          onRetry={() => profiles.refetch()}
        />
      )}

      <TableFrame title="Danh sách nhân viên" count={users.data?.totalElements}>
        {loading ? (
          <TableSkeleton rows={4} cols={4} />
        ) : failed ? (
          <div className="p-4">
            <ErrorState
              message={failed.error instanceof ApiError ? failed.error.message : "Không tải được danh sách."}
              onRetry={() => failed.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Nhân viên phòng khám"
            rows={rows}
            keyOf={(r) => r.user.id}
            columns={columns}
            empty={
              <EmptyState
                title="Chưa có nhân viên nào"
                description="Tạo tài khoản vai trò Nhân viên để họ đăng nhập và tự điền hồ sơ."
              />
            }
          />
        )}
      </TableFrame>
    </>
  );
}
