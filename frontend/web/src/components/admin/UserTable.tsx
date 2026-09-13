"use client";

import { useState, type FormEvent } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Lock, LockOpen, Search } from "lucide-react";
import { ApiError, ROLE_LABEL, authApi, customerLookupApi } from "@/lib/api";
import { useAuth } from "@/lib/auth/context";
import { LockAccountDialog } from "./LockAccountDialog";
import { formatDate } from "@/lib/utils/format";
import { USER_STATUS } from "@/lib/utils/status";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  Pagination,
  SelectField,
  StatusTag,
  TableFrame,
  TableSkeleton,
  Tag,
  controlClass,
  type Column,
} from "@/components/ui";
import type { CurrentUser, Role, UserStatus } from "@/types";

const PAGE_SIZE = 20;
const ROLES: Role[] = ["ADMIN", "DOCTOR", "STAFF", "CUSTOMER"];
const STATUSES: UserStatus[] = ["ACTIVE", "INACTIVE", "LOCKED"];

/**
 * Bảng tài khoản đọc từ GET /admin/users. Trang Tài khoản dùng đủ bộ lọc; trang Khách hàng
 * khoá cứng `role="CUSTOMER"`, ẩn cột vai trò và ghép điện thoại, địa chỉ, thú cưng từ
 * profile-service cho đúng các khách đang hiện trên trang.
 *
 * Không có nút xoá: xoá làm lịch khám và đơn hàng cũ mất người liên quan — dùng Khoá (CN-08).
 */
export function UserTable({ role, title, unitLabel }: { role?: Role; title: string; unitLabel: string }) {
  const [draft, setDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const [roleFilter, setRoleFilter] = useState<Role | "">("");
  const [status, setStatus] = useState<UserStatus | "">("");
  const [page, setPage] = useState(0);

  const effectiveRole = role ?? (roleFilter || undefined);
  const filters = { role: effectiveRole, status: status || undefined, keyword: keyword || undefined, page, size: PAGE_SIZE };

  const users = useQuery({
    queryKey: ["admin-users", filters],
    queryFn: () => authApi.listUsers(filters),
    placeholderData: keepPreviousData,
  });

  const { userId: myId } = useAuth();
  const [lockTarget, setLockTarget] = useState<CurrentUser | null>(null);

  const isCustomerList = role === "CUSTOMER";
  const pageIds = (users.data?.content ?? []).map((u) => u.id);
  const summaries = useQuery({
    queryKey: ["customer-summaries", pageIds],
    queryFn: () => customerLookupApi.summary(pageIds),
    enabled: isCustomerList && pageIds.length > 0,
    placeholderData: keepPreviousData,
  });
  const summaryOf = new Map((summaries.data ?? []).map((s) => [s.userId, s]));
  // Đang tải hiện "…"; không có hồ sơ (khách chưa từng mở trang hồ sơ) hoặc tải hỏng hiện "—".
  // Không lặp chữ "chưa khai" ở cả ba cột — một dòng đọc thành một bức tường chữ xám.
  const profileCell = (userId: string, pick: (s: NonNullable<ReturnType<typeof summaryOf.get>>) => React.ReactNode) => {
    if (summaries.isLoading) return <span className="text-bark">…</span>;
    const s = summaryOf.get(userId);
    return s ? pick(s) : <span className="text-bark">—</span>;
  };

  function submitSearch(e: FormEvent) {
    e.preventDefault();
    setKeyword(draft.trim());
    setPage(0);
  }

  const columns: Column<CurrentUser>[] = [
    {
      key: "name",
      header: "Họ tên",
      cell: (u) => <span className="font-medium text-ink">{`${u.firstName} ${u.lastName}`.trim()}</span>,
    },
    { key: "email", header: "Email", cell: (u) => <span className="break-all text-ink-soft">{u.email}</span> },
    ...(role
      ? []
      : [
          {
            key: "roles",
            header: "Vai trò",
            cell: (u: CurrentUser) => (
              <span className="flex flex-wrap gap-1">
                {u.roles.map((r) => (
                  <Tag key={r}>{ROLE_LABEL[r] ?? r}</Tag>
                ))}
              </span>
            ),
          },
        ]),
    ...(isCustomerList
      ? [
          {
            key: "phone",
            header: "Điện thoại",
            hideBelow: "md" as const,
            cell: (u: CurrentUser) => profileCell(u.id, (s) => s.phone || <span className="text-bark">—</span>),
          },
          {
            key: "address",
            header: "Địa chỉ",
            hideBelow: "lg" as const,
            cell: (u: CurrentUser) =>
              profileCell(u.id, (s) => (s.address ? <span className="line-clamp-2">{s.address}</span> : <span className="text-bark">—</span>)),
          },
          {
            key: "pets",
            header: "Thú cưng",
            cell: (u: CurrentUser) =>
              profileCell(u.id, (s) =>
                s.petNames.length ? s.petNames.join(", ") : <span className="text-bark">Chưa có</span>,
              ),
          },
        ]
      : []),
    { key: "status", header: "Trạng thái", cell: (u) => <StatusTag status={USER_STATUS[u.status]} /> },
    {
      key: "created",
      header: role === "CUSTOMER" ? "Đăng ký" : "Tạo lúc",
      hideBelow: "lg",
      cell: (u) => <span className="text-bark tnum">{formatDate(u.createdAt)}</span>,
    },
    {
      key: "actions",
      header: "",
      cell: (u) =>
        u.id === myId ? (
          <span className="text-sm text-bark">Bạn</span>
        ) : (
          <div className="flex justify-end">
            <Button variant={u.status === "LOCKED" ? "secondary" : "ghost"} size="sm" onClick={() => setLockTarget(u)}>
              {u.status === "LOCKED" ? <LockOpen aria-hidden className="size-3.5" /> : <Lock aria-hidden className="size-3.5" />}
              {u.status === "LOCKED" ? "Mở khoá" : "Khoá"}
            </Button>
          </div>
        ),
    },
  ];

  const hasFilter = Boolean(keyword || status || roleFilter);

  return (
    <>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <form onSubmit={submitSearch} className="flex min-w-0 flex-1 basis-72 items-end gap-2" role="search">
          <div className="min-w-0 flex-1">
            <label htmlFor="user-search" className="mb-1.5 block text-sm font-medium">
              Tìm theo tên hoặc email
            </label>
            <input
              id="user-search"
              type="search"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Ví dụ: Lan hoặc lan@gmail.com"
              className={`${controlClass} h-9`}
            />
          </div>
          <Button type="submit" variant="secondary">
            <Search aria-hidden className="size-4" />
            Tìm
          </Button>
        </form>

        {!role && (
          <div className="min-w-40">
            <SelectField
              label="Vai trò"
              value={roleFilter}
              onChange={(e) => {
                setRoleFilter(e.target.value as Role | "");
                setPage(0);
              }}
            >
              <option value="">Tất cả</option>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABEL[r]}
                </option>
              ))}
            </SelectField>
          </div>
        )}

        <div className="min-w-48">
          <SelectField
            label="Trạng thái"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as UserStatus | "");
              setPage(0);
            }}
          >
            <option value="">Tất cả</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {USER_STATUS[s].label}
              </option>
            ))}
          </SelectField>
        </div>
      </div>

      <TableFrame title={title} count={users.data?.totalElements}>
        {users.isLoading ? (
          <TableSkeleton rows={6} cols={4} />
        ) : users.isError ? (
          <div className="p-4">
            <ErrorState
              message={users.error instanceof ApiError ? users.error.message : "Không tải được danh sách tài khoản."}
              onRetry={() => users.refetch()}
            />
          </div>
        ) : (
          <>
            {isCustomerList && summaries.isError && (
              <div className="border-b border-line p-3">
                <ErrorState message="Không tải được điện thoại, địa chỉ và thú cưng — đang hiện “—”." onRetry={() => summaries.refetch()} />
              </div>
            )}
            <DataTable
              caption={title}
              rows={users.data?.content ?? []}
              keyOf={(u) => u.id}
              columns={columns}
              empty={
                hasFilter ? (
                  <EmptyState
                    title={`Không có ${unitLabel} nào khớp bộ lọc`}
                    action={
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setDraft("");
                          setKeyword("");
                          setStatus("");
                          setRoleFilter("");
                          setPage(0);
                        }}
                      >
                        Xoá bộ lọc
                      </Button>
                    }
                  />
                ) : (
                  <EmptyState title={`Chưa có ${unitLabel} nào`} />
                )
              }
            />
            {users.data && (
              <Pagination
                page={users.data.page}
                totalPages={users.data.totalPages}
                totalElements={users.data.totalElements}
                unitLabel={unitLabel}
                onChange={setPage}
              />
            )}
          </>
        )}
      </TableFrame>

      <LockAccountDialog user={lockTarget} onClose={() => setLockTarget(null)} />
    </>
  );
}
