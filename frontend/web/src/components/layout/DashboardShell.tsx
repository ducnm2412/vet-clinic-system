"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Globe, KeyRound, LogOut, Menu, PawPrint, X } from "lucide-react";
import { SidebarNav } from "./Sidebar";
import { ChangePasswordDialog } from "./ChangePasswordDialog";
import { NotificationBell } from "@/components/site/NotificationBell";
import { useAuth } from "@/lib/auth";
import { homeFor } from "@/config/nav";
import { ROLE_LABEL } from "@/lib/api";
import { cn } from "@/lib/utils/cn";
import type { Role } from "@/types";

/** Chữ màu nắng sau tên phòng khám — cho biết ngay đang ở khu vực của vai trò nào. */
const ROLE_TAG: Record<Role, string> = {
  ADMIN: "quản trị",
  DOCTOR: "bác sĩ",
  STAFF: "nhân viên",
  CUSTOMER: "",
};

/**
 * Khung dashboard: thanh bên xanh đậm chạy hết chiều cao màn hình, chứa luôn logo, chuông
 * thông báo và thẻ người dùng — nên vùng nội dung không cần thanh đầu trang, có trọn chiều
 * cao cho bảng và biểu mẫu. Màn hình hẹp: thanh bên thành ngăn kéo, mở bằng nút trên một
 * thanh mỏng ở đầu trang.
 */
export function DashboardShell({ role, children }: { role: Role; children: ReactNode }) {
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <div className="flex min-h-dvh bg-paper">
      <aside className="sticky top-0 z-40 hidden h-dvh w-64 shrink-0 lg:block">
        <SidebarPanel role={role} />
      </aside>

      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            aria-label="Đóng menu"
            onClick={() => setDrawerOpen(false)}
            className="absolute inset-0 bg-ink/40"
          />
          <div className="absolute inset-y-0 left-0 w-72 max-w-[85vw]">
            <SidebarPanel
              role={role}
              onNavigate={() => setDrawerOpen(false)}
              onClose={() => setDrawerOpen(false)}
            />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 bg-pine px-3 text-white lg:hidden">
          <button
            aria-label="Mở menu"
            onClick={() => setDrawerOpen(true)}
            className="grid size-10 place-items-center rounded-lg hover:bg-white/10"
          >
            <Menu aria-hidden className="size-5" />
          </button>
          <Brand role={role} compact />
          <div className="ml-auto">
            <NotificationBell variant="sidebar" />
          </div>
        </header>

        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-10 lg:py-9">{children}</main>
      </div>
    </div>
  );
}

function SidebarPanel({
  role,
  onNavigate,
  onClose,
}: {
  role: Role;
  onNavigate?: () => void;
  onClose?: () => void;
}) {
  return (
    <div className="relative isolate flex h-full flex-col overflow-hidden bg-pine text-white">
      {/* Vài vòng tròn mờ ở nửa dưới cho nền bớt phẳng — trang trí, không nhận chuột. */}
      <div aria-hidden className="pointer-events-none absolute -right-16 bottom-28 -z-10">
        <div className="size-64 rounded-full border border-white/[0.06]" />
        <div className="absolute inset-10 rounded-full border border-white/[0.06]" />
        <div className="absolute inset-20 rounded-full border border-white/[0.06]" />
      </div>

      <div className="flex items-center gap-2 px-5 pt-6">
        <Brand role={role} />
        {onClose && (
          <button
            aria-label="Đóng menu"
            onClick={onClose}
            className="ml-auto grid size-9 place-items-center rounded-lg text-white/80 hover:bg-white/10"
          >
            <X aria-hidden className="size-4" />
          </button>
        )}
      </div>

      <div className="flex items-center justify-end gap-1 px-4 pt-3 pb-2">
        <NotificationBell variant="sidebar" />
        <Link
          href="/?xem=website"
          target="_blank"
          aria-label="Mở website khách hàng"
          title="Mở website khách hàng"
          className="grid size-10 place-items-center rounded-full text-white/80 transition-colors hover:bg-white/10 hover:text-white"
        >
          <Globe aria-hidden className="size-5" />
        </Link>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto [scrollbar-color:rgb(255_255_255/0.25)_transparent] [scrollbar-width:thin]">
        <SidebarNav role={role} onNavigate={onNavigate} />
      </div>

      <UserCard />
    </div>
  );
}

/**
 * Bấm vào tên phòng khám thì về trang chính của vai trò mình, không về website khách hàng —
 * người đang làm việc mà rơi ra mặt tiền thì phải bấm ngược lại một lần nữa.
 */
function Brand({ role, compact }: { role: Role; compact?: boolean }) {
  return (
    <Link href={homeFor([role])} className="flex min-w-0 items-center gap-2.5">
      <span
        className={cn(
          "grid shrink-0 place-items-center rounded-lg bg-white/15 ring-1 ring-white/20",
          compact ? "size-8" : "size-10",
        )}
      >
        <PawPrint aria-hidden className={compact ? "size-4" : "size-5"} />
      </span>
      <span
        className={cn(
          "truncate font-semibold tracking-tight",
          compact ? "text-base" : "text-[19px]",
        )}
      >
        Vet Clinic <span className="text-sun">{ROLE_TAG[role]}</span>
      </span>
    </Link>
  );
}

function UserCard() {
  const { email, roles, signOut } = useAuth();
  const router = useRouter();
  const [changingPassword, setChangingPassword] = useState(false);
  const name = email?.split("@")[0] ?? "";
  const initial = (name[0] ?? "?").toUpperCase();

  return (
    <div className="p-3">
      <div className="flex items-center gap-3 rounded-xl bg-white/10 px-3 py-2.5">
        <span className="grid size-10 shrink-0 place-items-center rounded-full bg-sun text-base font-semibold text-pine">
          {initial}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold leading-tight" title={email ?? undefined}>
            {name}
          </p>
          <p className="truncate text-xs leading-tight text-white/65">
            {roles.map((r) => ROLE_LABEL[r]).join(", ")}
          </p>
        </div>
        <button
          aria-label="Đổi mật khẩu"
          title="Đổi mật khẩu"
          onClick={() => setChangingPassword(true)}
          className="grid size-9 shrink-0 place-items-center rounded-lg text-white/75 transition-colors hover:bg-white/10 hover:text-white"
        >
          <KeyRound aria-hidden className="size-4" />
        </button>
        <button
          aria-label="Đăng xuất"
          title="Đăng xuất"
          onClick={() => {
            signOut();
            router.push("/login");
          }}
          className="grid size-9 shrink-0 place-items-center rounded-lg text-white/75 transition-colors hover:bg-white/10 hover:text-white"
        >
          <LogOut aria-hidden className="size-4" />
        </button>
      </div>
      <ChangePasswordDialog open={changingPassword} onClose={() => setChangingPassword(false)} />
    </div>
  );
}

/** Tiêu đề trang, dùng thống nhất ở mọi màn hình trong dashboard. */
export function PageHeader({
  title,
  description,
  actions,
  back,
  badge,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  /** Liên kết quay lại trang danh sách, hiện phía trên tiêu đề (trang chi tiết). */
  back?: { href: string; label: string };
  /** Nhãn nhỏ cạnh tiêu đề, ví dụ vai trò hay trạng thái. */
  badge?: ReactNode;
}) {
  return (
    <div className="mb-6">
      {back && (
        <Link
          href={back.href}
          className="mb-5 inline-flex items-center gap-2 text-[15px] font-medium text-ink hover:text-pine"
        >
          <ArrowLeft aria-hidden className="size-4" />
          {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-[26px] font-semibold leading-tight tracking-tight text-ink">{title}</h1>
            {badge}
          </div>
          {description && <div className="mt-1.5 text-sm text-bark">{description}</div>}
        </div>
        {actions && <div className="flex items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}
