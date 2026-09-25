"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, EyeOff, Pencil, Plus } from "lucide-react";
import { ApiError, clinicServiceApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import type { ClinicService } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import { ClinicServiceFormDialog } from "@/components/booking/ClinicServiceFormDialog";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  IconButton,
  TableFrame,
  TableSkeleton,
  Tag,
  useToast,
  type Column,
} from "@/components/ui";

/**
 * VD-21: danh mục dịch vụ của phòng khám. Trước đây bốn dịch vụ là nội dung tĩnh trong code,
 * muốn thêm một dịch vụ phải sửa frontend rồi build lại.
 *
 * Không có nút xoá: lịch hẹn cũ vẫn trỏ tới dịch vụ này — ngừng cung cấp thì ẩn đi, giống sản
 * phẩm (VD-03) và tài khoản (CN-08).
 */
export default function AdminServicesPage() {
  const qc = useQueryClient();
  const toast = useToast();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ClinicService | null>(null);

  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });

  const toggle = useMutation({
    mutationFn: (s: ClinicService) => (s.active ? clinicServiceApi.hide(s.id) : clinicServiceApi.unhide(s.id)),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ["clinic-services"] });
      toast.success(saved.active ? `Đã cung cấp lại ${saved.name}` : `Đã ngừng ${saved.name}`);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không đổi được trạng thái dịch vụ."),
  });

  const columns: Column<ClinicService>[] = [
    {
      key: "name",
      header: "Dịch vụ",
      cell: (s) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-ink">{s.name}</p>
          <p className="text-xs text-bark">{s.slug}</p>
        </div>
      ),
    },
    {
      key: "description",
      header: "Mô tả",
      hideBelow: "lg",
      cell: (s) => <span className="line-clamp-2 text-bark">{s.description || "—"}</span>,
    },
    {
      key: "duration",
      header: "Thời lượng",
      numeric: true,
      cell: (s) => <span className="tnum whitespace-nowrap">{s.durationMinutes} phút</span>,
    },
    {
      key: "price",
      header: "Giá tham khảo",
      numeric: true,
      hideBelow: "sm",
      cell: (s) => (s.referencePrice === null ? <span className="text-bark">Báo sau</span> : formatPrice(s.referencePrice)),
    },
    {
      key: "active",
      header: "Trạng thái",
      cell: (s) => (s.active ? <Tag>Đang cung cấp</Tag> : <Tag className="text-bark">Đã ngừng</Tag>),
    },
    {
      key: "actions",
      header: "Thao tác",
      cell: (s) => (
        <div className="flex justify-end gap-1">
          <IconButton
            label={`Sửa ${s.name}`}
            variant="ghost"
            size="sm"
            onClick={() => {
              setEditing(s);
              setFormOpen(true);
            }}
          >
            <Pencil aria-hidden className="size-4" />
          </IconButton>
          <IconButton
            label={s.active ? `Ngừng ${s.name}` : `Cung cấp lại ${s.name}`}
            variant="ghost"
            size="sm"
            onClick={() => toggle.mutate(s)}
          >
            {s.active ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
          </IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Dịch vụ"
        description="Những việc phòng khám nhận làm. Danh sách này hiện trên trang chủ và trong bước chọn dịch vụ lúc khách đặt lịch."
        actions={
          <Button
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            <Plus aria-hidden className="size-4" />
            Thêm dịch vụ
          </Button>
        }
      />

      <TableFrame title="Danh mục dịch vụ" count={services.data?.length}>
        {services.isLoading ? (
          <TableSkeleton rows={4} cols={5} />
        ) : services.isError ? (
          <div className="p-4">
            <ErrorState
              message={services.error instanceof ApiError ? services.error.message : "Không tải được danh mục dịch vụ."}
              onRetry={() => services.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Dịch vụ của phòng khám"
            rows={services.data ?? []}
            keyOf={(s) => s.id}
            columns={columns}
            empty={
              <EmptyState
                title="Chưa có dịch vụ nào"
                description="Thêm dịch vụ để khách chọn được khi đặt lịch."
              />
            }
          />
        )}
      </TableFrame>

      {formOpen && (
        <ClinicServiceFormDialog open editing={editing} onClose={() => setFormOpen(false)} />
      )}
    </>
  );
}
