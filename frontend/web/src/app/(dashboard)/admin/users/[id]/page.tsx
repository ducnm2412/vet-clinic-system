"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Lock, LockOpen, MoreVertical, Trash2 } from "lucide-react";
import { PageHeader } from "@/components/layout/DashboardShell";
import { LockAccountDialog } from "@/components/admin/LockAccountDialog";
import { DeleteAccountDialog } from "@/components/admin/DeleteAccountDialog";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui";
import { ApiError, ROLE_LABEL, authApi, customerLookupApi, petLookupApi } from "@/lib/api";
import { useAuth } from "@/lib/auth/context";
import { formatDate, formatDateTime } from "@/lib/utils/format";
import { USER_STATUS } from "@/lib/utils/status";
import { cn } from "@/lib/utils/cn";
import type { CurrentUser } from "@/types";

const BACK = { href: "/admin/users", label: "Danh sách tài khoản" };

/**
 * Chi tiết một tài khoản: thông tin, thú cưng (với khách hàng), và Khoá / Xoá gom vào menu ⋮.
 * Chỉ dùng API có sẵn — backend chưa có API sửa tài khoản nên trang không có form sửa.
 */
export default function Page() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { userId: myId } = useAuth();
  const [deleted, setDeleted] = useState(false);
  const [lockTarget, setLockTarget] = useState<CurrentUser | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<CurrentUser | null>(null);

  const user = useQuery({
    queryKey: ["admin-user", id],
    queryFn: () => authApi.findUser(id),
    enabled: !deleted,
    // Bấm từ bảng thì dữ liệu đã nằm sẵn trong cache; đừng lật lại cả danh sách ngay khi vào trang.
    staleTime: 60_000,
  });

  if (user.isLoading) {
    return (
      <>
        <PageHeader title="Đang tải…" back={BACK} />
        <div className="space-y-4">
          <Skeleton className="h-44 w-full rounded-xl" />
          <Skeleton className="h-64 w-full rounded-xl" />
        </div>
      </>
    );
  }

  if (user.isError || !user.data) {
    const notFound = user.error instanceof ApiError && user.error.status === 404;
    return (
      <>
        <PageHeader title={notFound ? "Không tìm thấy tài khoản" : "Không tải được tài khoản"} back={BACK} />
        {notFound ? (
          <EmptyState title="Tài khoản này không tồn tại hoặc đã bị xoá." />
        ) : (
          <ErrorState
            message={user.error instanceof ApiError ? user.error.message : "Không tải được tài khoản."}
            onRetry={() => user.refetch()}
          />
        )}
      </>
    );
  }

  const u = user.data;
  const name = `${u.firstName} ${u.lastName}`.trim();
  const isMe = u.id === myId;
  const isCustomer = u.roles.includes("CUSTOMER");
  const deletable = u.roles.length > 0 && u.roles.every((r) => r === "DOCTOR" || r === "STAFF");
  const status = USER_STATUS[u.status];

  return (
    <>
      <PageHeader
        back={BACK}
        title={name || u.email}
        badge={u.roles.map((r) => (
          <span key={r} className="rounded-md bg-coral/10 px-2 py-0.5 text-[13px] font-medium text-coral-deep">
            {ROLE_LABEL[r] ?? r}
          </span>
        ))}
        description={
          <span className="inline-flex items-center gap-2 text-[15px] text-ink">
            <span aria-hidden className={cn("size-2 rounded-full", status.dot)} />
            {status.label}
          </span>
        }
        actions={
          isMe ? undefined : (
            <ActionMenu
              user={u}
              deletable={deletable}
              onLock={() => setLockTarget(u)}
              onDelete={() => setDeleteTarget(u)}
            />
          )
        }
      />

      <div className="space-y-5">
        <Card title="Thông tin">
          <dl className="grid gap-x-10 gap-y-3 px-5 py-4 md:grid-cols-2">
            <Info label="Email">
              <span className="break-all">{u.email}</span>
            </Info>
            <Info label="Vai trò">{u.roles.map((r) => ROLE_LABEL[r] ?? r).join(", ") || "—"}</Info>
            <Info label="Xác nhận email">{u.status === "INACTIVE" ? "Chưa xác nhận" : "Đã xác nhận"}</Info>
            <Info label="Trạng thái">{status.label}</Info>
            <Info label="Ngày tạo">
              <span className="tnum">{formatDateTime(u.createdAt)}</span>
            </Info>
            <Info label="Mã tài khoản">
              <span className="break-all font-mono text-[13px] text-ink-soft">{u.id}</span>
            </Info>
            {isCustomer && <CustomerContact userId={u.id} />}
          </dl>
        </Card>

        {isCustomer && <CustomerPets userId={u.id} />}
      </div>

      <LockAccountDialog user={lockTarget} onClose={() => setLockTarget(null)} />
      <DeleteAccountDialog
        user={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onDeleted={() => {
          setDeleted(true);
          router.replace(BACK.href);
        }}
      />
    </>
  );
}

function Card({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <section className="overflow-hidden rounded-xl border border-line bg-surface shadow-[0_1px_2px_rgb(26_36_32/0.04)]">
      <h2 className="border-b border-line px-5 py-3.5 font-semibold text-ink">{title}</h2>
      {children}
    </section>
  );
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 text-[15px]">
      <dt className="text-bark">{label}:</dt>
      <dd className="min-w-0 text-ink">{children}</dd>
    </div>
  );
}

/** Điện thoại, địa chỉ của khách nằm ở profile-service — khách chưa mở trang hồ sơ thì không có. */
function CustomerContact({ userId }: { userId: string }) {
  const summary = useQuery({
    queryKey: ["customer-summaries", [userId]],
    queryFn: () => customerLookupApi.summary([userId]),
  });
  const s = summary.data?.[0];
  const value = (v: string | null | undefined) =>
    summary.isLoading ? "…" : summary.isError ? "Không tải được" : v || "—";
  return (
    <>
      <Info label="Số điện thoại">{value(s?.phone)}</Info>
      <Info label="Địa chỉ">{value(s?.address)}</Info>
    </>
  );
}

function CustomerPets({ userId }: { userId: string }) {
  const pets = useQuery({
    queryKey: ["customer-pets", [userId]],
    queryFn: () => petLookupApi.byOwners([userId]),
  });
  const list = pets.data ?? [];
  return (
    <Card title={`Thú cưng${pets.data ? ` (${list.length})` : ""}`}>
      {pets.isLoading ? (
        <div className="px-5 py-4">
          <Skeleton className="h-10 w-full" />
        </div>
      ) : pets.isError ? (
        <div className="p-4">
          <ErrorState message="Không tải được danh sách thú cưng." onRetry={() => pets.refetch()} />
        </div>
      ) : list.length === 0 ? (
        <p className="px-5 py-6 text-sm text-bark">Khách này chưa có hồ sơ thú cưng nào.</p>
      ) : (
        <ul className="divide-y divide-line">
          {list.map((p) => (
            <li key={p.id} className="flex flex-wrap items-baseline gap-x-3 px-5 py-3 text-[15px]">
              <span className="font-medium text-ink">{p.name}</span>
              <span className="text-bark">
                {[p.species, p.breed].filter(Boolean).join(" · ")}
                {p.dateOfBirth && ` · sinh ${formatDate(p.dateOfBirth)}`}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/** Menu ⋮: Khoá / Mở khoá và Xoá. Không hiện khi xem chính tài khoản của mình. */
function ActionMenu({
  user,
  deletable,
  onLock,
  onDelete,
}: {
  user: CurrentUser;
  deletable: boolean;
  onLock: () => void;
  onDelete: () => void;
}) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const run = (fn: () => void) => () => {
    setOpen(false);
    fn();
  };
  const locked = user.status === "LOCKED";

  return (
    <div className="relative">
      <button
        type="button"
        aria-label="Thao tác khác"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="grid size-9 place-items-center rounded-[var(--radius-control)] border border-line-strong bg-surface text-ink hover:bg-paper"
      >
        <MoreVertical aria-hidden className="size-4" />
      </button>

      {open && (
        <>
          <button
            aria-label="Đóng menu"
            tabIndex={-1}
            className="fixed inset-0 z-10 cursor-default"
            onClick={() => setOpen(false)}
          />
          <div
            role="menu"
            className="absolute right-0 z-20 mt-2 w-60 animate-[dropIn_.15s_ease-out] overflow-hidden rounded-xl border border-line bg-surface py-1.5 shadow-[var(--shadow-dropdown)]"
          >
            <MenuItem icon={locked ? LockOpen : Lock} danger={!locked} onClick={run(onLock)}>
              {locked ? "Mở khoá" : "Khoá"}
            </MenuItem>
            {deletable && (
              <MenuItem icon={Trash2} danger onClick={run(onDelete)}>
                Xoá tài khoản
              </MenuItem>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function MenuItem({
  icon: Icon,
  danger,
  disabled,
  onClick,
  children,
}: {
  icon: typeof Lock;
  danger?: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "flex w-full items-center gap-3 px-4 py-2.5 text-left text-[15px] transition-colors disabled:opacity-50",
        danger ? "text-danger hover:bg-danger-wash" : "text-ink hover:bg-paper",
      )}
    >
      <Icon aria-hidden className="size-4 shrink-0" />
      {children}
    </button>
  );
}
