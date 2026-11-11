"use client";

import { Plus, Trash2, TriangleAlert } from "lucide-react";
import { TEMPLATE_MAX_ITEMS } from "@/lib/api";
import { MedicationCombobox } from "@/components/medical/MedicationCombobox";
import { Button, IconButton, TextField, controlClass } from "@/components/ui";
import type { PrescriptionTemplate, PrescriptionTemplateLineRequest } from "@/types";

/**
 * Một dòng thuốc đang soạn trong đơn mẫu. Khác PrescriptionEditor: thuốc ở đây phải gắn với một
 * thuốc trong danh mục (`medicationId`), nên ngoài tên hiển thị còn giữ id. Gõ vào ô tên thì id bị
 * xoá — dòng chỉ hợp lệ khi bác sĩ chọn lại từ gợi ý.
 */
export interface TemplateItemDraft {
  /** Khoá React ổn định khi xoá/thêm dòng (không dùng chỉ số vì dòng có state riêng trong ô gợi ý). */
  key: number;
  medicationId: string | null;
  medicationName: string;
  caution: string | null;
  /** Thuốc đã bị ẩn khỏi danh mục sau khi mẫu được tạo. */
  inactive: boolean;
  dosage: string;
  frequency: string;
  durationDays?: number;
  notes: string;
}

let seq = 0;

export function newDraft(): TemplateItemDraft {
  return {
    key: ++seq,
    medicationId: null,
    medicationName: "",
    caution: null,
    inactive: false,
    dosage: "",
    frequency: "",
    notes: "",
  };
}

export function draftsFrom(template: PrescriptionTemplate): TemplateItemDraft[] {
  return template.items.map((it) => ({
    key: ++seq,
    medicationId: it.medicationId,
    medicationName: it.medicationName,
    caution: it.medicationCaution,
    inactive: !it.medicationActive,
    dosage: it.dosage,
    frequency: it.frequency,
    durationDays: it.durationDays ?? undefined,
    notes: it.notes ?? "",
  }));
}

export function toRequest(d: TemplateItemDraft): PrescriptionTemplateLineRequest {
  return {
    medicationId: d.medicationId as string,
    dosage: d.dosage.trim(),
    frequency: d.frequency.trim(),
    durationDays: d.durationDays,
    notes: d.notes.trim() || undefined,
  };
}

export interface ItemErrors {
  medication?: string;
  dosage?: string;
  frequency?: string;
  durationDays?: string;
}

/** Giới hạn khớp PrescriptionTemplateItemRequest của backend (dosage/frequency ≤ 100, ngày ≥ 1). */
export function validateItem(d: TemplateItemDraft): ItemErrors {
  const errors: ItemErrors = {};
  if (!d.medicationId) {
    errors.medication = d.medicationName.trim()
      ? "Chọn thuốc từ gợi ý — mẫu chỉ nhận thuốc trong danh mục"
      : "Chọn thuốc từ danh mục";
  }
  const dosage = d.dosage.trim();
  if (!dosage) errors.dosage = "Nhập liều dùng";
  else if (dosage.length > 100) errors.dosage = "Tối đa 100 ký tự";
  const frequency = d.frequency.trim();
  if (!frequency) errors.frequency = "Nhập tần suất";
  else if (frequency.length > 100) errors.frequency = "Tối đa 100 ký tự";
  if (d.durationDays !== undefined && (!Number.isInteger(d.durationDays) || d.durationDays < 1)) {
    errors.durationDays = "Từ 1 ngày trở lên";
  }
  return errors;
}

export function hasErrors(e: ItemErrors): boolean {
  return Object.keys(e).length > 0;
}

/**
 * Các dòng thuốc của một đơn mẫu. Dạng có kiểm soát; lỗi chỉ hiện sau khi bấm lưu
 * (`showErrors`) để không đỏ cả dòng vừa mới thêm.
 */
export function TemplateItemsEditor({
  items,
  onChange,
  showErrors,
}: {
  items: TemplateItemDraft[];
  onChange: (items: TemplateItemDraft[]) => void;
  showErrors: boolean;
}) {
  function update(key: number, patch: Partial<TemplateItemDraft>) {
    onChange(items.map((it) => (it.key === key ? { ...it, ...patch } : it)));
  }

  return (
    <div>
      {items.length === 0 ? (
        <p className="rounded-[var(--radius-control)] border border-dashed border-line-strong px-4 py-5 text-center text-sm text-bark">
          Chưa có thuốc nào. Mẫu chỉ có chẩn đoán và hướng điều trị vẫn lưu được.
        </p>
      ) : (
        <ul className="space-y-3">
          {items.map((item, i) => {
            const err = showErrors ? validateItem(item) : {};
            return (
              <li
                key={item.key}
                className="rounded-[var(--radius-control)] border border-line bg-paper p-3"
              >
                <div className="flex items-start gap-2">
                  <div className="min-w-0 flex-1 space-y-3">
                    <MedicationCombobox
                      required
                      strict
                      value={item.medicationName}
                      error={err.medication}
                      // Gõ là mất liên kết với thuốc đã chọn; chọn gợi ý thì gắn lại. Patch của
                      // onSelect ghi đủ mọi trường vì nó chạy ngay sau onChange với cùng `items` cũ.
                      onChange={(medicationName) =>
                        update(item.key, {
                          medicationName,
                          medicationId: null,
                          caution: null,
                          inactive: false,
                        })
                      }
                      onSelect={(m) =>
                        update(item.key, {
                          medicationName: m.name,
                          medicationId: m.id,
                          caution: m.caution,
                          inactive: false,
                        })
                      }
                    />
                    {item.inactive && (
                      <p className="text-xs text-amber">
                        Thuốc này đã bị ẩn khỏi danh mục. Mẫu vẫn giữ nó, nhưng nên thay bằng thuốc
                        đang dùng.
                      </p>
                    )}
                    {item.caution && (
                      <p
                        role="note"
                        className="flex items-start gap-2 rounded-[var(--radius-control)] bg-amber-wash px-3 py-2 text-sm text-ink"
                      >
                        <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-amber" />
                        <span>
                          <span className="font-medium">Lưu ý:</span> {item.caution}
                        </span>
                      </p>
                    )}
                    <div className="grid gap-3 sm:grid-cols-3">
                      <TextField
                        label="Liều dùng"
                        placeholder="1 viên"
                        required
                        error={err.dosage}
                        value={item.dosage}
                        onChange={(e) => update(item.key, { dosage: e.target.value })}
                      />
                      <TextField
                        label="Tần suất"
                        placeholder="2 lần/ngày"
                        required
                        error={err.frequency}
                        value={item.frequency}
                        onChange={(e) => update(item.key, { frequency: e.target.value })}
                      />
                      <TextField
                        label="Số ngày"
                        type="number"
                        min={1}
                        error={err.durationDays}
                        value={item.durationDays ?? ""}
                        onChange={(e) =>
                          update(item.key, {
                            durationDays: e.target.value === "" ? undefined : Number(e.target.value),
                          })
                        }
                      />
                    </div>
                    <div>
                      <label
                        htmlFor={`tpl-note-${item.key}`}
                        className="mb-1.5 block text-sm font-medium text-ink"
                      >
                        Dặn dò
                      </label>
                      <input
                        id={`tpl-note-${item.key}`}
                        placeholder="Uống sau ăn"
                        value={item.notes}
                        onChange={(e) => update(item.key, { notes: e.target.value })}
                        className={`${controlClass} h-9`}
                      />
                    </div>
                  </div>

                  <IconButton
                    label={`Bỏ thuốc ${item.medicationName || `dòng ${i + 1}`}`}
                    variant="ghost"
                    size="sm"
                    onClick={() => onChange(items.filter((it) => it.key !== item.key))}
                  >
                    <Trash2 aria-hidden className="size-4" />
                  </IconButton>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      <Button
        variant="secondary"
        size="sm"
        disabled={items.length >= TEMPLATE_MAX_ITEMS}
        onClick={() => onChange([...items, newDraft()])}
        className="mt-3"
      >
        <Plus aria-hidden className="size-4" />
        Thêm thuốc
      </Button>
      {items.length >= TEMPLATE_MAX_ITEMS && (
        <p className="mt-1.5 text-xs text-bark">Một mẫu có tối đa {TEMPLATE_MAX_ITEMS} thuốc.</p>
      )}
    </div>
  );
}
