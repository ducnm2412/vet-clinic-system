"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Undo2, X } from "lucide-react";
import { ApiError, TEMPLATE_MAX_ITEMS, medicationApi, prescriptionTemplateApi } from "@/lib/api";
import { fold } from "@/lib/vnAdmin";
import { Button, Dialog, Skeleton, Tag, TextField, useToast } from "@/components/ui";
import type { Medication, PrescriptionItemRequest } from "@/types";

type Row =
  | { kind: "ok"; medication: Medication }
  | { kind: "missing" }
  | { kind: "hidden" }
  | { kind: "invalid"; reason: string };

/**
 * Lưu đơn đang soạn thành mẫu dùng chung. Đơn thuốc thật cho gõ tên tự do, còn mẫu bắt buộc
 * thuốc nằm trong danh mục — nên mỗi dòng được đối chiếu với danh mục theo tên (không phân biệt
 * dấu và hoa/thường) và bác sĩ xử lý dòng chưa khớp: thêm vào danh mục hoặc bỏ khỏi mẫu.
 *
 * Chỉ đọc nội dung form lúc mở, không đụng tới bệnh án: lưu mẫu không lưu bệnh án.
 */
export function SaveAsTemplateDialog({
  diagnosis,
  treatment,
  notes,
  items,
  onClose,
}: {
  diagnosis: string;
  treatment: string;
  notes: string;
  items: PrescriptionItemRequest[];
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const [name, setName] = useState(() => diagnosis.trim().split("\n")[0].slice(0, 150));
  const [nameError, setNameError] = useState<string>();
  // Chỉ số các dòng bị bỏ khỏi mẫu, và thuốc vừa thêm vào danh mục (khoá theo tên đã chuẩn hoá).
  const [dropped, setDropped] = useState<Set<number>>(new Set());
  const [created, setCreated] = useState<Record<string, Medication>>({});

  const names = [...new Set(items.map((it) => fold(it.medicationName)).filter(Boolean))];

  // Tìm cả thuốc đã ẩn để phân biệt "chưa có trong danh mục" với "có nhưng đã ẩn".
  const catalog = useQuery({
    queryKey: ["medications", "resolve", names],
    queryFn: async () => {
      const found: Record<string, Medication> = {};
      await Promise.all(
        names.map(async (n) => {
          const list = await medicationApi.search(n, { includeInactive: true, limit: 5 });
          const hit = list.find((m) => fold(m.name) === n);
          if (hit) found[n] = hit;
        }),
      );
      return found;
    },
    gcTime: 0,
  });

  function classify(it: PrescriptionItemRequest): Row {
    const dosage = it.dosage.trim();
    const frequency = it.frequency.trim();
    if (!it.medicationName.trim()) return { kind: "invalid", reason: "Chưa nhập tên thuốc" };
    if (!dosage || !frequency) return { kind: "invalid", reason: "Thiếu liều dùng hoặc tần suất" };
    if (dosage.length > 100 || frequency.length > 100) {
      return { kind: "invalid", reason: "Liều dùng hoặc tần suất quá dài (tối đa 100 ký tự)" };
    }
    if (it.durationDays !== undefined && (!Number.isInteger(it.durationDays) || it.durationDays < 1)) {
      return { kind: "invalid", reason: "Số ngày phải từ 1 trở lên" };
    }
    const key = fold(it.medicationName);
    const hit = created[key] ?? catalog.data?.[key];
    if (!hit) return { kind: "missing" };
    return hit.active ? { kind: "ok", medication: hit } : { kind: "hidden" };
  }

  const rows = items.map((it, i) => ({ item: it, index: i, row: classify(it) }));
  const kept = rows.filter((r) => !dropped.has(r.index));
  const blocking = kept.filter((r) => r.row.kind !== "ok");
  const tooMany = kept.length > TEMPLATE_MAX_ITEMS;

  function toggleDrop(index: number) {
    setDropped((prev) => {
      const next = new Set(prev);
      if (!next.delete(index)) next.add(index);
      return next;
    });
  }

  // Thuốc thêm từ đây chỉ có tên; đơn vị và lưu ý bổ sung sau ở Danh mục thuốc.
  const addToCatalog = useMutation({
    mutationFn: (medicationName: string) => medicationApi.create({ name: medicationName.trim() }),
    onSuccess: (m, medicationName) => {
      setCreated((prev) => ({ ...prev, [fold(medicationName)]: m }));
      qc.invalidateQueries({ queryKey: ["medications"] });
    },
    onError: (err) =>
      toast.error(
        err instanceof ApiError && err.status === 409
          ? "Tên này đã có trong danh mục (có thể đang bị ẩn). Kiểm tra ở mục Danh mục thuốc."
          : err instanceof ApiError
            ? err.message
            : "Không thêm được thuốc vào danh mục.",
      ),
  });

  const save = useMutation({
    mutationFn: () =>
      prescriptionTemplateApi.create({
        name: name.trim(),
        diagnosis: diagnosis.trim(),
        treatment: treatment.trim() || undefined,
        notes: notes.trim() || undefined,
        items: kept.map(({ item, row }) => ({
          // Đã qua kiểm tra: mọi dòng còn lại đều là "ok".
          medicationId: row.kind === "ok" ? row.medication.id : "",
          dosage: item.dosage.trim(),
          frequency: item.frequency.trim(),
          durationDays: item.durationDays,
          notes: item.notes?.trim() || undefined,
        })),
      }),
    onSuccess: (t) => {
      qc.invalidateQueries({ queryKey: ["prescription-templates"] });
      toast.success(`Đã lưu thành đơn mẫu "${t.name}"`);
      onClose();
    },
    onError: (err) => {
      // Trùng tên (không phân biệt hoa/thường và dấu): chỉ đúng ô tên.
      if (err instanceof ApiError && err.status === 409) {
        setNameError(err.message);
        return;
      }
      toast.error(err instanceof ApiError ? err.message : "Không lưu được đơn mẫu.");
    },
  });

  function submit() {
    const n = name.trim();
    if (!n) return setNameError("Nhập tên mẫu");
    if (n.length > 150) return setNameError("Tối đa 150 ký tự");
    setNameError(undefined);
    save.mutate();
  }

  const resolving = names.length > 0 && catalog.isLoading;
  const canSave = !resolving && !catalog.isError && blocking.length === 0 && !tooMany;

  return (
    <Dialog
      open
      onClose={onClose}
      wide
      title="Lưu đơn này thành mẫu"
      description="Mẫu dùng chung cho cả phòng khám và không chứa thông tin của ca khám này. Lưu mẫu không lưu bệnh án."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button loading={save.isPending} disabled={!canSave} onClick={submit}>
            Lưu thành mẫu
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <TextField
          label="Tên mẫu"
          required
          placeholder="VD: Viêm da dị ứng ở chó"
          error={nameError}
          value={name}
          onChange={(e) => {
            setName(e.target.value);
            setNameError(undefined);
          }}
        />

        <div className="rounded-[var(--radius-control)] border border-line bg-paper px-3 py-2 text-sm">
          <p className="text-bark">Chẩn đoán</p>
          <p className="whitespace-pre-line text-ink">{diagnosis.trim()}</p>
          {treatment.trim() && (
            <>
              <p className="mt-2 text-bark">Hướng điều trị</p>
              <p className="whitespace-pre-line text-ink">{treatment.trim()}</p>
            </>
          )}
        </div>

        <div>
          <h3 className="mb-2 text-sm font-medium text-ink">Thuốc trong mẫu</h3>

          {items.length === 0 ? (
            <p className="text-sm text-bark">Đơn chưa có thuốc — mẫu chỉ gồm chẩn đoán và điều trị.</p>
          ) : resolving ? (
            <Skeleton className="h-24 w-full" />
          ) : catalog.isError ? (
            <p className="text-sm text-danger">
              Không đối chiếu được với danh mục thuốc.{" "}
              <button
                type="button"
                onClick={() => catalog.refetch()}
                className="underline underline-offset-2"
              >
                Thử lại
              </button>
            </p>
          ) : (
            <ul className="divide-y divide-line rounded-[var(--radius-control)] border border-line">
              {rows.map(({ item, index, row }) => {
                const isDropped = dropped.has(index);
                return (
                  <li key={index} className="flex items-start gap-3 px-3 py-2.5">
                    <div className={`min-w-0 flex-1 ${isDropped ? "opacity-50" : ""}`}>
                      <p className={`text-sm font-medium text-ink ${isDropped ? "line-through" : ""}`}>
                        {item.medicationName.trim() || "(chưa nhập tên)"}
                      </p>
                      <p className="text-xs text-bark">
                        {[item.dosage.trim(), item.frequency.trim(), item.durationDays && `${item.durationDays} ngày`]
                          .filter(Boolean)
                          .join(" · ")}
                      </p>
                      {!isDropped && row.kind === "missing" && (
                        <p className="mt-1 text-xs text-amber">Chưa có trong danh mục thuốc.</p>
                      )}
                      {!isDropped && row.kind === "hidden" && (
                        <p className="mt-1 text-xs text-amber">
                          Thuốc này đã bị ẩn khỏi danh mục. Bật lại ở mục Danh mục thuốc, hoặc bỏ khỏi mẫu.
                        </p>
                      )}
                      {!isDropped && row.kind === "invalid" && (
                        <p className="mt-1 text-xs text-danger">
                          {row.reason}. Sửa ở đơn thuốc, hoặc bỏ khỏi mẫu.
                        </p>
                      )}
                    </div>

                    <div className="flex shrink-0 items-center gap-1.5">
                      {!isDropped && row.kind === "ok" && <Tag>Trong danh mục</Tag>}
                      {!isDropped && row.kind === "missing" && (
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={addToCatalog.isPending && addToCatalog.variables === item.medicationName}
                          onClick={() => addToCatalog.mutate(item.medicationName)}
                        >
                          <Plus aria-hidden className="size-4" />
                          Thêm vào danh mục
                        </Button>
                      )}
                      <Button size="sm" variant="ghost" onClick={() => toggleDrop(index)}>
                        {isDropped ? (
                          <>
                            <Undo2 aria-hidden className="size-4" />
                            Hoàn tác
                          </>
                        ) : (
                          <>
                            <X aria-hidden className="size-4" />
                            Bỏ khỏi mẫu
                          </>
                        )}
                      </Button>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}

          {tooMany && (
            <p className="mt-2 text-xs text-danger">
              Một mẫu có tối đa {TEMPLATE_MAX_ITEMS} thuốc — hãy bỏ bớt {kept.length - TEMPLATE_MAX_ITEMS} dòng.
            </p>
          )}
          {!resolving && blocking.length > 0 && !tooMany && (
            <p className="mt-2 text-xs text-bark">
              Còn {blocking.length} dòng chưa dùng được cho mẫu. Xử lý hết các dòng đó để lưu.
            </p>
          )}
        </div>
      </div>
    </Dialog>
  );
}
