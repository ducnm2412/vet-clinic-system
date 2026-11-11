"use client";

import { cn } from "@/lib/utils/cn";

/** Tab dạng viên — tab đang chọn nền cam nhạt; số đếm tuỳ chọn. Tràn ngang thì cuộn trong khung. */
export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
  className,
}: {
  tabs: Array<{ key: T; label: string; count?: number }>;
  value: T;
  onChange: (key: T) => void;
  className?: string;
}) {
  return (
    <div role="tablist" className={cn("-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-1", className)}>
      {tabs.map((t) => {
        const on = value === t.key;
        return (
          <button
            key={t.key}
            role="tab"
            type="button"
            aria-selected={on}
            onClick={() => onChange(t.key)}
            className={cn(
              "inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-4 py-2 text-sm transition-colors duration-200",
              on ? "bg-[#fff1e3] font-semibold text-[#f57c1f]" : "text-[#777777] hover:bg-[#fff8f1] hover:text-[#242424]",
            )}
          >
            {t.label}
            {t.count !== undefined && (
              <span
                className={cn(
                  "tnum rounded-full px-1.5 text-xs",
                  on ? "bg-white text-[#f57c1f]" : "bg-[#f4f4f4] text-[#777777]",
                )}
              >
                {t.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
