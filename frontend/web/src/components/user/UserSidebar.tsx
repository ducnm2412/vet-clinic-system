"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  CalendarDays,
  Headset,
  ChevronRight,
  LayoutGrid,
  LogOut,
  Menu,
  Package,
  PawPrint,
  Settings,
  X,
  type LucideIcon,
} from "lucide-react";
import { customerApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { mockFullName } from "@/lib/api/mock-account"; // MOCK-TẠM
import { cn } from "@/lib/utils/cn";
import { AuthedImage } from "@/components/AuthedImage";
import { DefaultAvatar } from "@/components/site/DefaultAvatar";
import { card } from "./ui";

type Item = { href: string; label: string; icon: LucideIcon; exact?: boolean; badge?: number };

const MAIN: Item[] = [
  { href: "/user", label: "Tài khoản của tôi", icon: LayoutGrid, exact: true },
  { href: "/user/orders", label: "Đơn hàng của tôi", icon: Package },
  { href: "/user/pets", label: "Thú cưng của tôi", icon: PawPrint },
  { href: "/user/appointments", label: "Lịch hẹn của tôi", icon: CalendarDays },
];

/** Tên hiển thị: họ tên nếu có (backend chưa có trường này), không thì phần trước @ của email. */
export function useDisplayName() {
  const { email } = useAuth();
  if (mockFullName) return mockFullName;
  if (!email) return "Bạn";
  return email
    .split("@")[0]
    .replace(/[._-]+/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function useCustomerProfile() {
  return useQuery({ queryKey: ["customer-profile"], queryFn: customerApi.me });
}

/** Ảnh đại diện khách: ảnh tải lên nếu có, không thì hình mặc định theo giới tính. */
export function CustomerAvatar({ className }: { className?: string }) {
  const profile = useCustomerProfile();
  return (
    <span className={cn("grid shrink-0 place-items-center overflow-hidden rounded-full bg-[#fff1e3]", className)}>
      <AuthedImage
        path={profile.data?.photoUrl}
        alt="Ảnh đại diện"
        className="size-full object-cover"
        fallback={<DefaultAvatar gender={profile.data?.gender} className="size-full" />}
      />
    </span>
  );
}

function UserProfileCard() {
  const name = useDisplayName();
  const { email } = useAuth();
  return (
    <div className="flex items-center gap-4 px-1 pb-5">
      <CustomerAvatar className="size-[72px] ring-4 ring-[#fff6ee]" />
      <div className="min-w-0">
        <p className="text-[18px] font-extrabold leading-tight text-[#1f4a3c]">{name}</p>
        <p className="truncate text-[13px] text-[#777777]">{email}</p>
      </div>
    </div>
  );
}

function NavList({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const router = useRouter();
  const { signOut } = useAuth();

  const isActive = (i: Item) => (i.exact ? pathname === i.href : pathname === i.href || pathname.startsWith(`${i.href}/`));
  const cls = (on: boolean) =>
    cn(
      "group flex items-center gap-3.5 rounded-2xl px-4 py-3 text-[15px] transition-colors duration-200",
      on ? "bg-[#fff1e3] font-semibold text-[#f57c1f]" : "text-[#242424] hover:bg-[#fff8f1] hover:text-[#f57c1f]",
    );

  const row = (i: Item) => {
    const on = isActive(i);
    return (
      <li key={i.href}>
        <Link href={i.href} onClick={onNavigate} aria-current={on ? "page" : undefined} className={cls(on)}>
          <i.icon aria-hidden className="size-5 shrink-0" />
          <span className="flex-1">{i.label}</span>
          {on && <ChevronRight aria-hidden className="size-4" />}
        </Link>
      </li>
    );
  };

  return (
    <nav aria-label="Tài khoản" className="border-t border-[#eeeeee] pt-4">
      <ul className="space-y-1">{MAIN.map(row)}</ul>
      <hr className="my-3 border-[#eeeeee]" />
      <ul className="space-y-1">
        {row({ href: "/user/settings", label: "Cài đặt tài khoản", icon: Settings })}
        <li>
          <button
            type="button"
            onClick={() => {
              onNavigate?.();
              signOut();
              router.push("/");
            }}
            className={cn(cls(false), "w-full text-left hover:text-[#e5484d]")}
          >
            <LogOut aria-hidden className="size-5 shrink-0" />
            Đăng xuất
          </button>
        </li>
      </ul>
    </nav>
  );
}

/**
 * Sidebar khách hàng. Desktop: thẻ dính bên trái. Dưới lg: thanh "Menu tài khoản" mở ngăn kéo.
 * Nằm trong layout /user nên giữ nguyên khi chuyển trang.
 */
export function UserSidebar() {
  const [open, setOpen] = useState(false);
  const name = useDisplayName();
  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <>
      {/* Desktop */}
      <div className="hidden h-fit space-y-5 lg:sticky lg:top-24 lg:block">
        <aside className={cn(card, "p-5")}>
          <UserProfileCard />
          <NavList />
        </aside>
        <SidebarPromo />
      </div>

      {/* Mobile / tablet */}
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-expanded={open}
        className={cn(card, "flex w-full items-center gap-3 p-3 text-left lg:hidden")}
      >
        <CustomerAvatar className="size-11" />
        <span className="min-w-0 flex-1">
          <span className="block truncate font-bold text-[#1f4a3c]">{name}</span>
          <span className="block text-xs text-[#777777]">Menu tài khoản</span>
        </span>
        <span className="grid size-10 place-items-center rounded-full bg-[#fff1e3] text-[#f57c1f]">
          <Menu aria-hidden className="size-5" />
        </span>
      </button>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu tài khoản">
          <button
            type="button"
            aria-label="Đóng menu"
            onClick={() => setOpen(false)}
            className="absolute inset-0 animate-[fadeIn_.2s_ease-out] bg-[#1f4a3c]/40 backdrop-blur-[2px]"
          />
          <div className="absolute inset-y-0 left-0 flex w-[min(20rem,86vw)] animate-[slideIn_.25s_ease-out] flex-col overflow-y-auto bg-white p-5">
            <div className="mb-2 flex justify-end">
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Đóng menu"
                className="grid size-10 place-items-center rounded-full hover:bg-[#fff1e3]"
              >
                <X aria-hidden className="size-5" />
              </button>
            </div>
            <UserProfileCard />
            <NavList onNavigate={() => setOpen(false)} />
          </div>
        </div>
      )}
    </>
  );
}

/** Banner nhỏ dưới sidebar: mời đặt lịch + số hotline hỗ trợ. */
function SidebarPromo() {
  return (
    <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#fff1e3] to-[#ffd9b5] p-5">
      <div className="relative z-10 max-w-[62%]">
        <p className="flex items-center gap-2 text-[15px] font-bold text-[#2b1d14]">
          <Headset aria-hidden className="size-5 text-[#1f4a3c]" /> Cần hỗ trợ?
        </p>
        <p className="mt-1.5 text-[13px] leading-snug text-[#5b4a3e]">Đặt lịch khám cho bé chỉ trong 1 phút, bác sĩ tư vấn tận tình.</p>
        <Link
          href="/appointments/create"
          className="press mt-4 inline-flex h-9 items-center gap-1.5 rounded-full bg-[#f57c1f] px-4 text-[13px] font-bold text-white shadow-[0_8px_18px_-8px_rgba(245,124,31,0.9)] hover:bg-[#e06a10]"
        >
          <CalendarDays aria-hidden className="size-4" /> Đặt lịch <ArrowRight aria-hidden className="nudge size-3.5" />
        </Link>
      </div>
      <div aria-hidden className="absolute -bottom-6 -right-6 size-36 overflow-hidden rounded-full border-4 border-white/70">
        <Image src="/img/cho-con.jpg" alt="" fill sizes="144px" className="object-cover" />
      </div>
    </div>
  );
}
