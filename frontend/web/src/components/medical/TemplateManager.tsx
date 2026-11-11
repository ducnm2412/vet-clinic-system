"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Search, Trash2 } from "lucide-react";
import { ApiError, prescriptionTemplateApi } from "@/lib/api";
import { useDebouncedValue } from "@/lib/useDebouncedValue";
import type { PrescriptionTemplateSummary } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import { TemplateFormDialog } from "@/components/medical/TemplateFormDialog";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  IconButton,
  TableFrame,
  TableSkeleton,
  controlClass,
  useToast,
  type Column,
} from "@/components/ui";

/**
 * Quản lý đơn mẫu dùng chung của phòng khám: tạo, sửa, xoá. Mẫu chỉ là cách điền sẵn bệnh án
 * cho nhanh, nên xoá hẳn được — bệnh án đã lập không trỏ vào mẫu.
 */
export function TemplateManager() {
  const qc = useQueryClient();
  const toast = useToast();

  const [keyword, setKeyword] = useState("");
  // undefined = đóng; null = tạo mới; chuỗi = sửa mẫu có id đó.
  const [formFor, setFormFor] = useState<string | null | undefined>(undefined);
  const [deleting, setDeleting] = useState<PrescriptionTemplateSummary | null>(null);

  const q = useDebouncedValue(keyword.trim());

  const templates = useQuery({
    queryKey: ["prescription-templates", "list", q],
    queryFn: () => prescriptionTemplateApi.list(q),
    placeholderData: keepPreviousData,
  });

  const remove = useMutation({
    mutationFn: (t: PrescriptionTemplateSummary) => prescriptionTemplateApi.remove(t.id),
    onSuccess: (_, t) => {
      qc.invalidateQueries({ queryKey: ["prescription-templates"] });
      toast.success(`Đã xoá đơn mẫu ${t.name}`);
      setDeleting(null);
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : "Không xoá được đơn mẫu.");
      setDeleting(null);
    },
  });

  const rows = templates.data ?? [];

  const columns: Column<PrescriptionTemplateSummary>[] = [
    {
      key: "name",
      header: "Tên mẫu",
      cell: (t) => <span className="font-medium text-ink">{t.name}</span>,
    },
    {
      key: "diagnosis",
      header: "Chẩn đoán",
      hideBelow: "md",
      cell: (t) => <span className="line-clamp-2 text-bark">{t.diagnosis}</span>,
    },
    {
      key: "items",
      header: "Thuốc",
      hideBelow: "sm",
      cell: (t) => (t.itemCount > 0 ? `${t.itemCount} thuốc` : <span className="text-bark">—</span>),
    },
    {
      key: "actions",
      header: "Thao tác",
      cell: (t) => (
        <div className="flex justify-end gap-1">
          <IconButton label={`Sửa ${t.name}`} variant="ghost" size="sm" onClick={() => setFormFor(t.id)}>
            <Pencil aria-hidden className="size-4" />
          </IconButton>
          <IconButton label={`Xoá ${t.name}`} variant="ghost" size="sm" onClick={() => setDeleting(t)}>
            <Trash2 aria-hidden className="size-4" />
          </IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Đơn mẫu"
        description="Đơn thuốc soạn sẵn cho các bệnh thường gặp, dùng chung cho cả phòng khám. Bác sĩ chọn mẫu khi lập bệnh án để điền sẵn rồi chỉnh lại theo từng ca."
        actions={
          <Button onClick={() => setFormFor(null)}>
            <Plus aria-hidden className="size-4" />
            Tạo đơn mẫu
          </Button>
        }
      />

      <TableFrame
        title="Đơn mẫu"
        count={templates.data?.length}
        actions={
          <div className="relative">
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
              className={`${controlClass} h-9 w-56 pl-9`}
            />
          </div>
        }
      >
        {templates.isLoading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : templates.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                templates.error instanceof ApiError
                  ? templates.error.message
                  : "Không tải được danh sách đơn mẫu."
              }
              onRetry={() => templates.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Đơn mẫu của phòng khám"
            rows={rows}
            keyOf={(t) => t.id}
            columns={columns}
            empty={
              q ? (
                <EmptyState
                  title="Không có đơn mẫu nào khớp"
                  description={`Không thấy mẫu nào có tên chứa "${q}".`}
                />
              ) : (
                <EmptyState
                  title="Chưa có đơn mẫu nào"
                  description="Tạo mẫu cho các bệnh hay gặp để bác sĩ kê đơn nhanh hơn. Thuốc trong mẫu phải có sẵn trong Danh mục thuốc."
                />
              )
            }
          />
        )}
      </TableFrame>

      {formFor !== undefined && (
        <TemplateFormDialog editingId={formFor} onClose={() => setFormFor(undefined)} />
      )}

      <Dialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title={`Xoá đơn mẫu ${deleting?.name ?? ""}?`}
        description="Mẫu bị xoá hẳn và không khôi phục được. Các bệnh án đã lập từ mẫu này không bị ảnh hưởng."
        footer={
          <>
            <Button variant="secondary" onClick={() => setDeleting(null)}>
              Huỷ
            </Button>
            <Button
              variant="danger"
              loading={remove.isPending}
              onClick={() => deleting && remove.mutate(deleting)}
            >
              Xoá đơn mẫu
            </Button>
          </>
        }
      >
        <p className="text-sm text-bark">Bạn có chắc muốn xoá mẫu này không?</p>
      </Dialog>
    </>
  );
}
