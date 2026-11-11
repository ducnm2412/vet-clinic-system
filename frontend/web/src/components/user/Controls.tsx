"use client";

import { ChevronDown, Search } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export function SearchInput({
  value,
  onChange,
  placeholder = "Tìm kiếm…",
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
}) {
  return (
    <label className={cn("relative block", className)}>
      <span className="sr-only">{placeholder}</span>
      <Search aria-hidden className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-[#999999]" />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="h-10 w-full rounded-full border border-[#eeeeee] bg-white pl-10 pr-4 text-sm outline-none transition focus:border-[#f57c1f] focus:ring-2 focus:ring-[#f57c1f]/15"
      />
    </label>
  );
}

export function FilterDropdown<T extends string>({
  value,
  onChange,
  options,
  label,
  className,
}: {
  value: T;
  onChange: (v: T) => void;
  options: Array<{ value: T; label: string }>;
  label: string;
  className?: string;
}) {
  return (
    <label className={cn("relative block", className)}>
      <span className="sr-only">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value as T)}
        className="h-10 w-full appearance-none rounded-full border border-[#eeeeee] bg-white pl-4 pr-9 text-sm text-[#242424] outline-none transition focus:border-[#f57c1f]"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <ChevronDown aria-hidden className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-[#777777]" />
    </label>
  );
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Phân trang" className="mt-8 flex items-center justify-center gap-1.5">
      {Array.from({ length: totalPages }, (_, i) => (
        <button
          key={i}
          type="button"
          aria-current={i === page ? "page" : undefined}
          onClick={() => onChange(i)}
          className={cn(
            "tnum grid size-10 place-items-center rounded-full text-sm transition-colors",
            i === page ? "bg-[#f57c1f] font-semibold text-white" : "text-[#242424] hover:bg-[#fff1e3]",
          )}
        >
          {i + 1}
        </button>
      ))}
    </nav>
  );
}
