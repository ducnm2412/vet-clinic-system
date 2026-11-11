"use client";

import { useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { Plus, Trash2, TriangleAlert } from "lucide-react";
import { medicationApi } from "@/lib/api";
import { useDebouncedValue } from "@/lib/useDebouncedValue";
import { fold } from "@/lib/vnAdmin";
import { MedicationCombobox } from "@/components/medical/MedicationCombobox";
import { Button, IconButton, TextField, controlClass } from "@/components/ui";
import type { Medication, PrescriptionItemRequest } from "@/types";

/**
 * Với mỗi tên thuốc đang có trong đơn, tra danh mục xem có đúng thuốc đó không (khớp tên không
 * phân biệt dấu và hoa/thường; tên trong danh mục là duy nhất nên không nhầm). Nhờ vậy lưu ý
 * hiện ra cả khi bác sĩ gõ tay đủ tên mà không bấm gợi ý, và cả với đơn mở lại từ bệnh án cũ.
 *
 * Tra theo tên chứ không lưu "dòng này đã chọn thuốc nào": xoá hay đổi chỗ dòng không làm lệch.
 */
function useCatalogMatches(items: PrescriptionItemRequest[]): Map<string, Medication> {
  const key = items.map((it) => fold(it.medicationName)).join("\n");
  // Gõ xong mới tra, nếu không mỗi phím bấm là một lượt gọi backend.
  const settled = useDebouncedValue(key, 400);
  const names = useMemo(
    () => [...new Set(settled.split("\n").filter((n) => n.length >= 2))],
    [settled],
  );

  return useQueries({
    queries: names.map((name) => ({
      queryKey: ["medications", "lookup", name],
      queryFn: () => medicationApi.search(name, { limit: 5 }),
      staleTime: 60_000,
    })),
    combine: (results) => {
      const matches = new Map<string, Medication>();
      results.forEach((r, i) => {
        // Tên khớp đúng luôn xếp đầu trong số tên bắt đầu bằng cùng chữ, nên 5 dòng là đủ.
        const hit = r.data?.find((m) => fold(m.name) === names[i]);
        if (hit) matches.set(names[i], hit);
      });
      return matches;
    },
  });
}

/**
 * Soạn đơn thuốc. Mỗi dòng là một loại thuốc kèm liều và cách dùng — đúng bốn trường
 * backend nhận, không thêm trường nào chưa có chỗ lưu.
 *
 * Tên thuốc có gợi ý từ danh mục và hiện lưu ý của thuốc nếu có; danh mục chỉ để gợi ý nên
 * thuốc ngoài danh mục vẫn gõ tay kê được.
 *
 * Dùng dạng có kiểm soát để trang bệnh án giữ toàn bộ trạng thái ở một chỗ, tránh cảnh
 * bấm lưu mà mất dòng vừa gõ dở.
 */
export function PrescriptionEditor({
  items,
  onChange,
  disabled,
}: {
  items: PrescriptionItemRequest[];
  onChange: (items: PrescriptionItemRequest[]) => void;
  disabled?: boolean;
}) {
  const catalog = useCatalogMatches(items);

  function update(index: number, patch: Partial<PrescriptionItemRequest>) {
    onChange(items.map((it, i) => (i === index ? { ...it, ...patch } : it)));
  }

  function add() {
    onChange([...items, { medicationName: "", dosage: "", frequency: "" }]);
  }

  function remove(index: number) {
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <div>
      {items.length === 0 ? (
        <p className="rounded-[var(--radius-control)] border border-dashed border-line-strong px-4 py-6 text-center text-sm text-bark">
          Chưa kê thuốc nào. Bệnh án vẫn lưu được nếu ca này không cần dùng thuốc.
        </p>
      ) : (
        <ul className="space-y-3">
          {items.map((item, i) => {
            const caution = catalog.get(fold(item.medicationName))?.caution;
            return (
            <li
              key={i}
              className="rounded-[var(--radius-control)] border border-line bg-paper p-3"
            >
              <div className="flex items-start gap-2">
                <div className="min-w-0 flex-1 space-y-3">
                  <MedicationCombobox
                    required
                    disabled={disabled}
                    value={item.medicationName}
                    onChange={(medicationName) => update(i, { medicationName })}
                  />
                  {caution && (
                    <p
                      role="note"
                      className="flex items-start gap-2 rounded-[var(--radius-control)] bg-amber-wash px-3 py-2 text-sm text-ink"
                    >
                      <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-amber" />
                      <span>
                        <span className="font-medium">Lưu ý:</span> {caution}
                      </span>
                    </p>
                  )}
                  <div className="grid gap-3 sm:grid-cols-3">
                    <TextField
                      label="Liều dùng"
                      placeholder="1 viên"
                      required
                      disabled={disabled}
                      value={item.dosage}
                      onChange={(e) => update(i, { dosage: e.target.value })}
                    />
                    <TextField
                      label="Tần suất"
                      placeholder="2 lần/ngày"
                      required
                      disabled={disabled}
                      value={item.frequency}
                      onChange={(e) => update(i, { frequency: e.target.value })}
                    />
                    <TextField
                      label="Số ngày"
                      type="number"
                      min={1}
                      disabled={disabled}
                      value={item.durationDays ?? ""}
                      onChange={(e) =>
                        update(i, {
                          durationDays: e.target.value === "" ? undefined : Number(e.target.value),
                        })
                      }
                    />
                  </div>
                  <div>
                    <label
                      htmlFor={`presc-note-${i}`}
                      className="mb-1.5 block text-sm font-medium text-ink"
                    >
                      Dặn dò
                    </label>
                    <input
                      id={`presc-note-${i}`}
                      disabled={disabled}
                      placeholder="Uống sau ăn"
                      value={item.notes ?? ""}
                      onChange={(e) => update(i, { notes: e.target.value || undefined })}
                      className={`${controlClass} h-9`}
                    />
                  </div>
                </div>

                <IconButton
                  label={`Bỏ thuốc ${item.medicationName || `dòng ${i + 1}`}`}
                  variant="ghost"
                  size="sm"
                  disabled={disabled}
                  onClick={() => remove(i)}
                >
                  <Trash2 aria-hidden className="size-4" />
                </IconButton>
              </div>
            </li>
            );
          })}
        </ul>
      )}

      {!disabled && (
        <Button variant="secondary" size="sm" onClick={add} className="mt-3">
          <Plus aria-hidden className="size-4" />
          Thêm thuốc
        </Button>
      )}
    </div>
  );
}
