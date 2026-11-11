"use client";

import { useId, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { TriangleAlert } from "lucide-react";
import { medicationApi } from "@/lib/api";
import { useDebouncedValue } from "@/lib/useDebouncedValue";
import { cn } from "@/lib/utils/cn";
import type { Medication } from "@/types";
import { Field, controlClass } from "@/components/ui";

/** Số gợi ý hiện tối đa — đủ để chọn, không biến thành một danh sách dài phải cuộn. */
const SUGGESTION_LIMIT = 8;

/**
 * Ô nhập tên thuốc có gợi ý từ danh mục. Gõ vài chữ (không cần dấu) thì hiện các thuốc khớp.
 *
 * Gõ tự do vẫn được: danh mục chỉ để gợi ý, không phải danh sách bắt buộc — nếu chặn cứng thì
 * danh mục thiếu một thuốc là bác sĩ không kê được. Đơn thuốc lưu tên dạng chữ, nên chọn gợi ý
 * hay gõ tay đều ra cùng một giá trị `value`.
 *
 * `strict` dành cho đơn mẫu, nơi thuốc bắt buộc nằm trong danh mục: ô vẫn gõ được để tìm, nhưng
 * lời nhắn khi không khớp đổi sang "hãy thêm vào danh mục". Việc giữ `medicationId` do chỗ gọi lo
 * (xoá khi gõ, đặt khi `onSelect`) — combobox không biết dòng nào đã gắn thuốc nào.
 *
 * Bàn phím theo mẫu ARIA combobox: ↑ ↓ chọn dòng, Enter nhận, Esc đóng. Enter chỉ bị chặn khi
 * đang chọn một dòng gợi ý, để không lỡ gửi form khi bác sĩ chỉ muốn chốt tên vừa gõ.
 */
export function MedicationCombobox({
  label = "Tên thuốc",
  value,
  onChange,
  onSelect,
  required,
  disabled,
  strict,
  error,
}: {
  label?: string;
  value: string;
  /** Mỗi lần gõ. */
  onChange: (text: string) => void;
  /** Khi chọn một gợi ý (sau onChange với tên đầy đủ của thuốc). */
  onSelect?: (medication: Medication) => void;
  required?: boolean;
  disabled?: boolean;
  /** Chỉ nhận thuốc trong danh mục — đổi lời nhắn khi gõ không khớp. */
  strict?: boolean;
  error?: string;
}) {
  const inputId = useId();
  const listId = `${inputId}-list`;

  const [open, setOpen] = useState(false);
  // -1 = chưa chọn dòng nào. Đặt lại mỗi khi gõ, vì danh sách đổi theo chữ mới.
  const [active, setActive] = useState(-1);

  const q = useDebouncedValue(value.trim());

  const suggestions = useQuery({
    queryKey: ["medications", "suggest", q],
    queryFn: () => medicationApi.search(q, { limit: SUGGESTION_LIMIT }),
    enabled: open && !disabled,
    // Giữ gợi ý cũ trong lúc tải gợi ý mới để danh sách không chớp.
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });

  const options = suggestions.data ?? [];
  const showList = open && !disabled;

  function choose(m: Medication) {
    onChange(m.name);
    onSelect?.(m);
    setOpen(false);
    setActive(-1);
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
      setActive((i) => (options.length === 0 ? -1 : (i + 1) % options.length));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setOpen(true);
      setActive((i) => (options.length === 0 ? -1 : (i <= 0 ? options.length - 1 : i - 1)));
    } else if (e.key === "Enter") {
      if (showList && active >= 0 && options[active]) {
        e.preventDefault();
        choose(options[active]);
      }
    } else if (e.key === "Escape") {
      if (showList) {
        e.preventDefault();
        setOpen(false);
        setActive(-1);
      }
    }
  }

  return (
    <Field label={label} required={required} error={error} htmlFor={inputId}>
      <div className="relative">
        <input
          id={inputId}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          aria-activedescendant={showList && active >= 0 ? `${inputId}-opt-${active}` : undefined}
          autoComplete="off"
          disabled={disabled}
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          // Chọn bằng chuột dùng onMouseDown (preventDefault) nên blur chỉ xảy ra khi thật sự rời ô.
          onBlur={() => {
            setOpen(false);
            setActive(-1);
          }}
          onKeyDown={onKeyDown}
          className={cn(controlClass, "h-9", error && "border-danger")}
        />

        {showList && (
          <ul
            id={listId}
            role="listbox"
            aria-label="Gợi ý thuốc"
            className="absolute z-20 mt-1 max-h-72 w-full overflow-auto rounded-[var(--radius-control)] border border-line-strong bg-surface py-1 shadow-[var(--shadow-dialog)]"
          >
            {options.map((m, i) => (
              <li
                key={m.id}
                id={`${inputId}-opt-${i}`}
                role="option"
                aria-selected={i === active}
                onMouseDown={(e) => {
                  e.preventDefault();
                  choose(m);
                }}
                onMouseEnter={() => setActive(i)}
                className={cn(
                  "cursor-pointer px-3 py-2 text-sm",
                  i === active ? "bg-moss-wash" : "bg-surface",
                )}
              >
                <div className="flex items-baseline gap-2">
                  <span className="font-medium text-ink">{m.name}</span>
                  {m.unit && <span className="text-xs text-bark">{m.unit}</span>}
                </div>
                {m.caution && (
                  <p className="mt-0.5 flex items-start gap-1 text-xs text-bark">
                    <TriangleAlert aria-hidden className="mt-0.5 size-3 shrink-0 text-amber" />
                    <span className="line-clamp-1">{m.caution}</span>
                  </p>
                )}
              </li>
            ))}

            {options.length === 0 && (
              <li role="presentation" className="px-3 py-2 text-xs text-bark">
                {suggestions.isLoading
                  ? "Đang tìm…"
                  : suggestions.isError
                    ? strict
                      ? "Không tải được gợi ý. Thử gõ lại."
                      : "Không tải được gợi ý — vẫn dùng được tên bạn gõ."
                    : strict
                      ? q
                        ? "Không có trong danh mục — thêm thuốc ở mục Danh mục thuốc trước."
                        : "Danh mục còn trống — thêm thuốc ở mục Danh mục thuốc trước."
                      : q
                        ? "Không có trong danh mục — vẫn dùng được tên bạn gõ."
                        : "Danh mục còn trống — gõ tên thuốc để kê."}
              </li>
            )}
          </ul>
        )}
      </div>
    </Field>
  );
}
