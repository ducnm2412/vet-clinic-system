import type { ReactNode } from "react";
import Link from "next/link";
import { ChevronRight } from "lucide-react";

/** Đầu trang: breadcrumb + tiêu đề xanh đậm + mô tả + nút thao tác bên phải. */
export function PageHeader({
  title,
  description,
  crumbs = [],
  action,
}: {
  title: string;
  description?: string;
  crumbs?: Array<{ href?: string; label: string }>;
  action?: ReactNode;
}) {
  const trail = [{ href: "/user", label: "Tài khoản" }, ...crumbs];
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <nav aria-label="Breadcrumb" className="mb-2 flex flex-wrap items-center gap-1 text-[13px] text-[#777777]">
          <Link href="/" className="hover:text-[#f57c1f]">
            Trang chủ
          </Link>
          {trail.map((c, i) => (
            <span key={c.label} className="flex items-center gap-1">
              <ChevronRight aria-hidden className="size-3.5" />
              {c.href && i < trail.length - 1 ? (
                <Link href={c.href} className="hover:text-[#f57c1f]">
                  {c.label}
                </Link>
              ) : (
                <span aria-current="page" className="text-[#242424]">
                  {c.label}
                </span>
              )}
            </span>
          ))}
        </nav>
        <h1 className="text-[26px] font-extrabold leading-tight text-[#1f4a3c] sm:text-[28px]">{title}</h1>
        {description && <p className="mt-1.5 text-[15px] text-[#777777]">{description}</p>}
      </div>
      {action}
    </header>
  );
}
