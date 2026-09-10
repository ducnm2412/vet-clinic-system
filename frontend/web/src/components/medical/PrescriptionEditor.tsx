"use client";

import { Plus, Trash2 } from "lucide-react";
import { Button, IconButton, TextField, controlClass } from "@/components/ui";
import type { PrescriptionItemRequest } from "@/types";

/**
 * Soạn đơn thuốc. Mỗi dòng là một loại thuốc kèm liều và cách dùng — đúng bốn trường
 * backend nhận, không thêm trường nào chưa có chỗ lưu.
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
          {items.map((item, i) => (
            <li
              key={i}
              className="rounded-[var(--radius-control)] border border-line bg-paper p-3"
            >
              <div className="flex items-start gap-2">
                <div className="min-w-0 flex-1 space-y-3">
                  <TextField
                    label="Tên thuốc"
                    required
                    disabled={disabled}
                    value={item.medicationName}
                    onChange={(e) => update(i, { medicationName: e.target.value })}
                  />
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
          ))}
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
