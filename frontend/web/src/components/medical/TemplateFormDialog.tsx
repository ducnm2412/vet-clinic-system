"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, prescriptionTemplateApi } from "@/lib/api";
import {
  TemplateItemsEditor,
  draftsFrom,
  hasErrors,
  toRequest,
  validateItem,
  type TemplateItemDraft,
} from "@/components/medical/TemplateItemsEditor";
import {
  Button,
  Dialog,
  ErrorState,
  Skeleton,
  TextAreaField,
  TextField,
  useToast,
} from "@/components/ui";
import type { PrescriptionTemplate } from "@/types";

// Giới hạn khớp PrescriptionTemplateRequest: tên ≤ 150, chẩn đoán bắt buộc.
const schema = z.object({
  name: z.string().trim().min(1, "Nhập tên mẫu").max(150, "Tối đa 150 ký tự"),
  diagnosis: z.string().trim().min(1, "Nhập chẩn đoán"),
  treatment: z.string().optional(),
  notes: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

const DESCRIPTION =
  "Bác sĩ chọn mẫu này khi lập bệnh án để điền sẵn chẩn đoán, hướng điều trị và đơn thuốc. Sửa hay xoá mẫu không làm đổi các bệnh án đã lập.";

/**
 * Tạo hoặc sửa một đơn mẫu. Danh sách chỉ có tóm tắt, nên khi sửa phải tải chi tiết trước:
 * lớp ngoài lo việc tải, lớp trong (TemplateForm) chỉ dựng khi đã có dữ liệu để defaultValues đúng.
 */
export function TemplateFormDialog({
  editingId,
  onClose,
}: {
  /** Có thì là sửa mẫu đó, null thì là tạo mới. */
  editingId: string | null;
  onClose: () => void;
}) {
  const detail = useQuery({
    queryKey: ["prescription-templates", "detail", editingId],
    queryFn: () => prescriptionTemplateApi.get(editingId as string),
    enabled: editingId !== null,
    // Luôn lấy bản mới nhất: người khác có thể vừa sửa mẫu này.
    gcTime: 0,
  });

  if (editingId === null) return <TemplateForm template={null} onClose={onClose} />;
  if (detail.data) return <TemplateForm template={detail.data} onClose={onClose} />;

  return (
    <Dialog open onClose={onClose} wide title="Sửa đơn mẫu" description={DESCRIPTION}>
      {detail.isError ? (
        <ErrorState
          message={
            detail.error instanceof ApiError ? detail.error.message : "Không tải được đơn mẫu."
          }
          onRetry={() => detail.refetch()}
        />
      ) : (
        <Skeleton className="h-40 w-full" />
      )}
    </Dialog>
  );
}

function TemplateForm({
  template,
  onClose,
}: {
  template: PrescriptionTemplate | null;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  const [items, setItems] = useState<TemplateItemDraft[]>(() =>
    template ? draftsFrom(template) : [],
  );
  // Lỗi từng dòng thuốc chỉ hiện sau lần bấm lưu đầu tiên.
  const [showErrors, setShowErrors] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: template?.name ?? "",
      diagnosis: template?.diagnosis ?? "",
      treatment: template?.treatment ?? "",
      notes: template?.notes ?? "",
    },
  });

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      // Ô trống gửi là undefined để backend lưu null.
      const body = {
        name: v.name,
        diagnosis: v.diagnosis,
        treatment: v.treatment?.trim() || undefined,
        notes: v.notes?.trim() || undefined,
        items: items.map(toRequest),
      };
      return template
        ? prescriptionTemplateApi.update(template.id, body)
        : prescriptionTemplateApi.create(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["prescription-templates"] });
      toast.success(template ? "Đã lưu đơn mẫu" : "Đã tạo đơn mẫu");
      onClose();
    },
    onError: (err) => {
      // Trùng tên (không phân biệt hoa/thường và dấu): chỉ đúng ô tên thay vì báo chung chung.
      if (err instanceof ApiError && err.status === 409) {
        setError("name", { message: err.message });
        return;
      }
      // 400 như thêm thuốc đã bị ẩn: backend nói rõ thuốc nào, nên nói lại nguyên văn.
      toast.error(err instanceof ApiError ? err.message : "Không lưu được đơn mẫu.");
    },
  });

  const submit = handleSubmit(
    (v) => {
      setShowErrors(true);
      if (items.some((it) => hasErrors(validateItem(it)))) {
        toast.error("Còn dòng thuốc chưa hợp lệ — xem các ô báo đỏ.");
        return;
      }
      save.mutate(v);
    },
    () => setShowErrors(true),
  );

  return (
    <Dialog
      open
      onClose={onClose}
      wide
      title={template ? `Sửa ${template.name}` : "Tạo đơn mẫu"}
      description={DESCRIPTION}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="template-form" type="submit" loading={save.isPending}>
            {template ? "Lưu thay đổi" : "Tạo đơn mẫu"}
          </Button>
        </>
      }
    >
      <form id="template-form" onSubmit={submit} noValidate className="space-y-4">
        <TextField
          label="Tên mẫu"
          required
          placeholder="VD: Viêm da dị ứng ở chó"
          error={errors.name?.message}
          {...register("name")}
        />
        <TextAreaField
          label="Chẩn đoán"
          required
          rows={2}
          error={errors.diagnosis?.message}
          {...register("diagnosis")}
        />
        <TextAreaField label="Hướng điều trị" rows={2} {...register("treatment")} />
        <TextAreaField label="Ghi chú" rows={2} {...register("notes")} />

        <div>
          <h3 className="mb-2 text-sm font-medium text-ink">Thuốc trong mẫu</h3>
          <TemplateItemsEditor items={items} onChange={setItems} showErrors={showErrors} />
        </div>
      </form>
    </Dialog>
  );
}
