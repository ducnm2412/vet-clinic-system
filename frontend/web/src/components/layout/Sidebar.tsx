"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV } from "@/config/nav";
import { cn } from "@/lib/utils/cn";
import type { Role } from "@/types";

/**
 * Điều hướng chính, đặt trên nền xanh đậm của thanh bên. Mục đang mở có nền sáng hơn và
 * một vạch màu nắng ở mép trái. Mục dựa trên API chưa có được đánh dấu bằng một chấm nhỏ —
 * người dùng biết trước là vào sẽ chưa có dữ liệu, thay vì bấm vào rồi tưởng hệ thống hỏng.
 */
export function SidebarNav({ role, onNavigate }: { role: Role; onNavigate?: () => void }) {
  const pathname = usePathname();
  const sections = NAV[role] ?? [];

  return (
    <nav aria-label="Điều hướng chính" className="px-3 pb-4">
      {sections.map((section, i) => (
        <div key={section.title ?? i} className={cn(i > 0 && "mt-5")}>
          {section.title && (
            <p className="mb-1.5 px-4 text-xs font-semibold uppercase tracking-wider text-white/55">
              {section.title}
            </p>
          )}
          <ul className="space-y-1">
            {section.items.map((item) => {
              const active =
                pathname === item.href ||
                (item.href !== `/${role.toLowerCase()}` && pathname.startsWith(`${item.href}/`));
              const Icon = item.icon;
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    onClick={onNavigate}
                    aria-current={active ? "page" : undefined}
                    className={cn(
                      "relative flex items-center gap-3 rounded-xl px-4 py-2.5 text-[15px]",
                      "transition-colors duration-[120ms]",
                      active
                        ? "bg-white/15 font-semibold text-white"
                        : "font-medium text-white/85 hover:bg-white/[0.08] hover:text-white",
                    )}
                  >
                    {active && (
                      <span
                        aria-hidden
                        className="absolute -left-3 top-1.5 bottom-1.5 w-1 rounded-r-full bg-sun"
                      />
                    )}
                    <Icon aria-hidden className="size-[18px] shrink-0" />
                    <span className="min-w-0 flex-1 truncate">{item.label}</span>
                    {item.pending && (
                      <span
                        className="size-1.5 shrink-0 rounded-full bg-sun"
                        title="Backend chưa có API cho màn hình này"
                      />
                    )}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );
}
