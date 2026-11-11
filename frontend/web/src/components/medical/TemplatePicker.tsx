"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { Search, TriangleAlert } from "lucide-react";
import { ApiError, prescriptionTemplateApi } from "@/lib/api";
import { useDebouncedValue } from "@/lib/useDebouncedValue";
import {
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  Skeleton,
  controlClass,
  useToast,
} from "@/components/ui";
import type { PrescriptionTemplate } from "@/types";

/**
 * Chọn một đơn mẫu để điền sẵn vào bệnh án. Chỉ điền vào form: bệnh án vẫn lưu như thường và
 * không giữ id mẫu, nên luồng thanh toán/trạng thái đơn không bị đụng tới.
 *
 * Có bước xác nhận khi form đã có nội dung (điền mẫu sẽ thay thế chứ không trộn) hoặc khi mẫu có
 * thuốc đã bị ẩn khỏi danh mục — bác sĩ cần biết trước khi nó vào đơn.
 */
export function TemplatePicker({
  open,
  onClose,
  hasContent,
  onApply,
}: {
  open: boolean;
  onClose: () => void;
  /** Form hiện đã có chẩn đoán/điều trị/ghi chú/thuốc — điền mẫu sẽ ghi đè. */
  hasContent: boolean;
  onApply: (template: PrescriptionTemplate) => void;
}) {
  const toast = useToast();
  const [keyword, setKeyword] = useState("");
  const [pending, setPending] = useState<PrescriptionTemplate | null>(null);
  const q = useDebouncedValue(keyword.trim());

  const templates = useQuery({
    queryKey: ["prescription-templates", "list", q],
    queryFn: () => prescriptionTemplateApi.list(q),
    enabled: open,
    placeholderData: keepPreviousData,
  });

  function finish(t: PrescriptionTemplate) {
    onApply(t);
    close();
  }

  function close() {
    setPending(null);
    setKeyword("");
    onClose();
  }

  // Danh sách chỉ có tóm tắt nên phải tải chi tiết của mẫu được chọn mới có thuốc để điền.
  const load = useMutation({
    mutationFn: (id: string) => prescriptionTemplateApi.get(id),
    onSuccess: (t) => {
      if (hasContent || t.items.some((it) => !it.medicationActive)) setPending(t);
      else finish(t);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không mở được đơn mẫu này."),
  });

  if (pending) {
    const hidden = pending.items.filter((it) => !it.medicationActive);
    return (
      <Dialog
        open={open}
        onClose={close}
        title={`Điền từ mẫu ${pending.name}?`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setPending(null)}>
              Quay lại
            </Button>
            <Button onClick={() => finish(pending)}>Điền mẫu này</Button>
          </>
        }
      >
        <div className="space-y-3 text-sm">
          {hasContent && (
            <p className="text-ink">
              Chẩn đoán, điều trị, ghi chú và đơn thuốc đang soạn sẽ được{" "}
              <span className="font-medium">thay bằng nội dung của mẫu</span>. Chưa lưu bệnh án thì
              phần cũ sẽ mất.
            </p>
          )}
          {hidden.length > 0 && (
            <p className="flex items-start gap-2 rounded-[var(--radius-control)] bg-amber-wash px-3 py-2 text-ink">
              <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-amber" />
              <span>
                Mẫu có thuốc đã ẩn khỏi danh mục:{" "}
                <span className="font-medium">{hidden.map((it) => it.medicationName).join(", ")}</span>
                . Các thuốc này vẫn được điền, hãy kiểm tra lại trước khi lưu.
              </span>
            </p>
          )}
        </div>
      </Dialog>
    );
  }

  const rows = templates.data ?? [];

  return (
    <Dialog
      open={open}
      onClose={close}
      wide
      title="Chọn đơn mẫu"
      description="Điền sẵn chẩn đoán, điều trị và đơn thuốc từ mẫu của phòng khám, sau đó chỉnh lại theo từng ca."
      footer={
        <Button variant="secondary" onClick={close}>
          Đóng
        </Button>
      }
    >
      <div className="relative mb-3">
        <Search
          aria-hidden
          className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-bark"
        />
        <input
          type="search"
          aria-label="Tìm đơn mẫu theo tên"
          placeholder="Tìm đơn mẫu…"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          className={`${controlClass} h-9 pl-9`}
        />
      </div>

      {templates.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : templates.isError ? (
        <ErrorState
          message={
            templates.error instanceof ApiError ? templates.error.message : "Không tải được đơn mẫu."
          }
          onRetry={() => templates.refetch()}
        />
      ) : rows.length === 0 ? (
        <EmptyState
          title={q ? "Không có đơn mẫu nào khớp" : "Chưa có đơn mẫu nào"}
          description={
            q
              ? `Không thấy mẫu nào có tên chứa "${q}".`
              : "Tạo mẫu ở mục Đơn mẫu, hoặc lưu đơn đang soạn thành mẫu sau khi kê xong."
          }
        />
      ) : (
        <ul className="divide-y divide-line rounded-[var(--radius-control)] border border-line">
          {rows.map((t) => (
            <li key={t.id}>
              <button
                type="button"
                disabled={load.isPending}
                onClick={() => load.mutate(t.id)}
                className="flex w-full items-start gap-3 px-3 py-2.5 text-left hover:bg-paper disabled:cursor-wait disabled:opacity-60"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium text-ink">{t.name}</span>
                  <span className="mt-0.5 line-clamp-1 block text-sm text-bark">{t.diagnosis}</span>
                </span>
                <span className="shrink-0 pt-0.5 text-xs text-bark">
                  {load.isPending && load.variables === t.id
                    ? "Đang mở…"
                    : t.itemCount > 0
                      ? `${t.itemCount} thuốc`
                      : "Không thuốc"}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Dialog>
  );
}
