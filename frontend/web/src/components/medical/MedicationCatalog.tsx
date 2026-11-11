"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, EyeOff, Pencil, Plus, Search } from "lucide-react";
import { ApiError, MEDICATION_MAX_LIMIT, medicationApi } from "@/lib/api";
import { useDebouncedValue } from "@/lib/useDebouncedValue";
import type { Medication } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import { MedicationFormDialog } from "@/components/medical/MedicationFormDialog";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  IconButton,
  TableFrame,
  TableSkeleton,
  Tag,
  controlClass,
  useToast,
  type Column,
} from "@/components/ui";

/**
 * Danh mục thuốc dùng chung của phòng khám. Bác sĩ và ADMIN cùng quản lý nên đây là một
 * component dùng cho cả hai trang, mỗi vai trò chỉ có một trang mỏng bọc ngoài.
 *
 * Không có nút xoá: đơn mẫu trỏ vào thuốc, nên ngừng dùng thì ẩn khỏi gợi ý (giống dịch vụ
 * và sản phẩm). Đơn đã kê không bị ảnh hưởng vì lưu tên thuốc dạng chữ.
 */
export function MedicationCatalog() {
  const qc = useQueryClient();
  const toast = useToast();

  const [keyword, setKeyword] = useState("");
  const [includeInactive, setIncludeInactive] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Medication | null>(null);

  // Gõ xong mới tìm: mỗi phím một request là lãng phí, và danh sách sẽ nháy liên tục.
  const q = useDebouncedValue(keyword.trim());

  const medications = useQuery({
    queryKey: ["medications", "catalog", q, includeInactive],
    queryFn: () => medicationApi.search(q, { includeInactive, limit: MEDICATION_MAX_LIMIT }),
    // Giữ danh sách cũ trong lúc tải kết quả mới để bảng không chớp thành khung xương.
    placeholderData: keepPreviousData,
  });

  const toggle = useMutation({
    // hide trả 204 còn unhide trả Medication — gộp về void để hai nhánh cùng một kiểu.
    mutationFn: async (m: Medication) => {
      if (m.active) await medicationApi.hide(m.id);
      else await medicationApi.unhide(m);
    },
    onSuccess: (_, m) => {
      qc.invalidateQueries({ queryKey: ["medications"] });
      toast.success(m.active ? `Đã ẩn ${m.name} khỏi gợi ý` : `Đã bật lại ${m.name}`);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không đổi được trạng thái thuốc."),
  });

  const rows = medications.data ?? [];

  const columns: Column<Medication>[] = [
    {
      key: "name",
      header: "Thuốc",
      cell: (m) => <span className="font-medium text-ink">{m.name}</span>,
    },
    {
      key: "unit",
      header: "Đơn vị",
      hideBelow: "sm",
      cell: (m) => m.unit || <span className="text-bark">—</span>,
    },
    {
      key: "caution",
      header: "Lưu ý",
      hideBelow: "md",
      cell: (m) => <span className="line-clamp-2 text-bark">{m.caution || "—"}</span>,
    },
    {
      key: "active",
      header: "Trạng thái",
      cell: (m) => (m.active ? <Tag>Đang dùng</Tag> : <Tag className="text-bark">Đã ẩn</Tag>),
    },
    {
      key: "actions",
      header: "Thao tác",
      cell: (m) => (
        <div className="flex justify-end gap-1">
          <IconButton
            label={`Sửa ${m.name}`}
            variant="ghost"
            size="sm"
            onClick={() => {
              setEditing(m);
              setFormOpen(true);
            }}
          >
            <Pencil aria-hidden className="size-4" />
          </IconButton>
          <IconButton
            label={m.active ? `Ẩn ${m.name}` : `Bật lại ${m.name}`}
            variant="ghost"
            size="sm"
            disabled={toggle.isPending}
            onClick={() => toggle.mutate(m)}
          >
            {m.active ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
          </IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Danh mục thuốc"
        description="Thuốc dùng chung của phòng khám. Bác sĩ gõ tên thuốc khi kê đơn sẽ được gợi ý từ đây, kèm lưu ý nếu có. Sửa hay ẩn thuốc không làm đổi các đơn đã kê."
        actions={
          <Button
            onClick={() => {
              setEditing(null);
              setFormOpen(true);
            }}
          >
            <Plus aria-hidden className="size-4" />
            Thêm thuốc
          </Button>
        }
      />

      <TableFrame
        title="Thuốc"
        count={medications.data?.length}
        actions={
          <>
            <label className="flex items-center gap-1.5 text-sm text-bark">
              <input
                type="checkbox"
                checked={includeInactive}
                onChange={(e) => setIncludeInactive(e.target.checked)}
                className="size-4"
              />
              Hiện cả thuốc đã ẩn
            </label>
            <div className="relative">
              <Search
                aria-hidden
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-bark"
              />
              <input
                type="search"
                aria-label="Tìm thuốc theo tên"
                placeholder="Tìm thuốc…"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                className={`${controlClass} h-9 w-56 pl-9`}
              />
            </div>
          </>
        }
      >
        {medications.isLoading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : medications.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                medications.error instanceof ApiError
                  ? medications.error.message
                  : "Không tải được danh mục thuốc."
              }
              onRetry={() => medications.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Danh mục thuốc của phòng khám"
            rows={rows}
            keyOf={(m) => m.id}
            columns={columns}
            empty={
              q ? (
                <EmptyState
                  title="Không có thuốc nào khớp"
                  description={`Không thấy thuốc nào có tên chứa "${q}". Thử gõ ít chữ hơn, hoặc thêm thuốc mới.`}
                />
              ) : (
                <EmptyState
                  title="Danh mục còn trống"
                  description="Thêm thuốc để bác sĩ được gợi ý khi kê đơn."
                />
              )
            }
          />
        )}

        {/* Backend trả tối đa 50 dòng và chưa có phân trang: đủ 50 thì có thể còn thuốc chưa hiện. */}
        {rows.length >= MEDICATION_MAX_LIMIT && (
          <p className="border-t border-line px-4 py-2.5 text-xs text-bark">
            Đang hiện {MEDICATION_MAX_LIMIT} thuốc đầu tiên. Gõ tên vào ô tìm kiếm để lọc thêm.
          </p>
        )}
      </TableFrame>

      {formOpen && (
        <MedicationFormDialog open editing={editing} onClose={() => setFormOpen(false)} />
      )}
    </>
  );
}
