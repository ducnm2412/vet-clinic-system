"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { LogOut, Menu, X } from "lucide-react";
import { SidebarNav } from "./Sidebar";
import { IconButton } from "@/components/ui";
import { useAuth } from "@/lib/auth";
import { homeFor } from "@/config/nav";
import { ROLE_LABEL } from "@/lib/api";
import type { Role } from "@/types";

/**
 * Khung dashboard: thanh bên cố định trên máy tính, ngăn kéo trên màn hình hẹp.
 * Chọn thanh bên thay vì thanh dưới kiểu ứng dụng di động, vì đây là công cụ làm việc
 * nhiều mục — thanh dưới chỉ chứa được 4-5 mục là hết chỗ.
 */
export function DashboardShell({ role, children }: { role: Role; children: ReactNode }) {
  const { email, roles, signOut } = useAuth();
  const router = useRouter();
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Ngăn kéo tự đóng khi bấm một mục trong menu — SidebarNav gọi onNavigate,
  // nên không cần theo dõi pathname bằng effect.

  return (
    <div className="flex min-h-dvh">
      <aside className="hidden w-60 shrink-0 border-r border-line bg-surface lg:block">
        <Brand role={role} />
        <SidebarNav role={role} />
      </aside>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            aria-label="Đóng menu"
            onClick={() => setDrawerOpen(false)}
            className="absolute inset-0 bg-ink/30"
          />
          <div className="absolute inset-y-0 left-0 w-64 overflow-y-auto border-r border-line bg-surface">
            <div className="flex items-center justify-between pr-2">
              <Brand role={role} />
              <IconButton label="Đóng menu" variant="ghost" size="sm" onClick={() => setDrawerOpen(false)}>
                <X aria-hidden className="size-4" />
              </IconButton>
            </div>
            <SidebarNav role={role} onNavigate={() => setDrawerOpen(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center gap-3 border-b border-line bg-surface px-4">
          <IconButton
            label="Mở menu"
            variant="ghost"
            size="sm"
            className="lg:hidden"
            onClick={() => setDrawerOpen(true)}
          >
            <Menu aria-hidden className="size-4" />
          </IconButton>

          <div className="ml-auto flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm leading-tight text-ink">{email}</p>
              <p className="text-xs leading-tight text-bark">
                {roles.map((r) => ROLE_LABEL[r]).join(", ")}
              </p>
            </div>
            <IconButton
              label="Đăng xuất"
              variant="secondary"
              size="sm"
              onClick={() => {
                signOut();
                router.push("/login");
              }}
            >
              <LogOut aria-hidden className="size-4" />
            </IconButton>
          </div>
        </header>

        <main className="min-w-0 flex-1 bg-paper px-4 py-6 lg:px-6">{children}</main>
      </div>
    </div>
  );
}

/**
 * Bấm vào tên phòng khám thì về trang chính của vai trò mình, không về website khách hàng —
 * người đang làm việc mà rơi ra mặt tiền thì phải bấm ngược lại một lần nữa.
 */
function Brand({ role }: { role: Role }) {
  return (
    <div className="flex h-14 items-center border-b border-line px-5">
      <Link href={homeFor([role])} className="text-[15px] font-semibold tracking-tight text-ink">
        <span className="text-moss">Thú y</span> Vet Clinic
      </Link>
    </div>
  );
}

/** Tiêu đề trang, dùng thống nhất ở mọi màn hình trong dashboard. */
export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-5 flex flex-wrap items-start gap-3">
      <div className="min-w-0 flex-1">
        <h1 className="font-[family-name:var(--font-display)] text-[25px] leading-tight text-ink">
          {title}
        </h1>
        {description && <p className="mt-1 text-sm text-bark">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}
