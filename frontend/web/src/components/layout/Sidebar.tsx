"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV } from "@/config/nav";
import { cn } from "@/lib/utils/cn";
import type { Role } from "@/types";

/**
 * Điều hướng chính. Mục dựa trên API chưa có được đánh dấu bằng một chấm nhỏ — người dùng
 * biết trước là vào sẽ chưa có dữ liệu, thay vì bấm vào rồi tưởng hệ thống hỏng.
 */
export function SidebarNav({ role, onNavigate }: { role: Role; onNavigate?: () => void }) {
  const pathname = usePathname();
  const sections = NAV[role] ?? [];

  return (
    <nav aria-label="Điều hướng chính" className="px-3 py-4">
      {sections.map((section, i) => (
        <div key={section.title ?? i} className={cn(i > 0 && "mt-6")}>
          {section.title && (
            <p className="mb-1.5 px-3 text-xs font-medium text-bark">{section.title}</p>
          )}
          <ul className="space-y-0.5">
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
                      "flex items-center gap-2.5 rounded-[var(--radius-control)] px-3 py-2 text-sm",
                      "transition-colors duration-[120ms]",
                      active
                        ? "bg-moss-wash font-medium text-ink"
                        : "text-ink-soft hover:bg-paper hover:text-ink",
                    )}
                  >
                    <Icon
                      aria-hidden
                      className={cn("size-4 shrink-0", active ? "text-moss" : "text-bark")}
                    />
                    <span className="min-w-0 flex-1 truncate">{item.label}</span>
                    {item.pending && (
                      <span
                        className="size-1.5 shrink-0 rounded-full bg-amber"
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
