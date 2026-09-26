"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { ApiError, categoryApi } from "@/lib/api";
import { Button, Dialog, EmptyState, ErrorState, IconButton, Skeleton, TextField, useToast } from "@/components/ui";
import type { Category } from "@/types";

/**
 * CN-27: quản lý danh mục sản phẩm.
 *
 * Danh mục là danh sách ngắn, thỉnh thoảng mới sửa — không đáng một trang riêng trong thanh
 * điều hướng, nên để thành hộp thoại mở từ trang Sản phẩm, ngay cạnh chỗ dùng nó.
 *
 * Ở đây có nút xoá, khác với sản phẩm (VD-03): danh mục không mang lịch sử kho, và
 * `product-service` đã chặn xoá danh mục còn sản phẩm.
 */
export function CategoryDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();

  const [editing, setEditing] = useState<Category | null>(null);
  const [form, setForm] = useState({ name: "", slug: "", description: "" });
  const [formOpen, setFormOpen] = useState(false);

  const categories = useQuery({ queryKey: ["categories"], queryFn: categoryApi.list });

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: form.name.trim(),
        slug: form.slug.trim(),
        description: form.description.trim() || undefined,
      };
      return editing ? categoryApi.update(editing.id, body) : categoryApi.create(body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["categories"] });
      toast.success(editing ? "Đã lưu danh mục" : "Đã thêm danh mục");
      setFormOpen(false);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được danh mục."),
  });

  const remove = useMutation({
    mutationFn: (id: string) => categoryApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["categories"] });
      toast.success("Đã xoá danh mục");
    },
    onError: (err) =>
      // Danh mục còn sản phẩm thì backend trả 409 kèm lý do — nói lại nguyên văn cho người dùng.
      toast.error(err instanceof ApiError ? err.message : "Không xoá được danh mục."),
  });

  function openCreate() {
    setEditing(null);
    setForm({ name: "", slug: "", description: "" });
    setFormOpen(true);
  }

  function openEdit(c: Category) {
    setEditing(c);
    setForm({ name: c.name, slug: c.slug, description: c.description ?? "" });
    setFormOpen(true);
  }

  const list = categories.data ?? [];

  return (
    <>
      <Dialog
        open={open && !formOpen}
        onClose={onClose}
        title="Danh mục sản phẩm"
        description="Nhóm hàng dùng để lọc ở cửa hàng và khi nhập kho."
        footer={
          <>
            <Button variant="secondary" onClick={onClose}>
              Đóng
            </Button>
            <Button onClick={openCreate}>
              <Plus aria-hidden className="size-4" />
              Thêm danh mục
            </Button>
          </>
        }
      >
        {categories.isLoading ? (
          <Skeleton className="h-32 w-full" />
        ) : categories.isError ? (
          <ErrorState message="Không tải được danh mục." onRetry={() => categories.refetch()} />
        ) : list.length === 0 ? (
          <EmptyState title="Chưa có danh mục nào" description="Thêm một nhóm hàng để bắt đầu." />
        ) : (
          <ul className="divide-y divide-line rounded-[var(--radius-control)] border border-line">
            {list.map((c) => (
              <li key={c.id} className="flex items-center gap-3 p-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-ink">{c.name}</p>
                  <p className="truncate text-xs text-bark">
                    {c.slug}
                    {c.description ? ` · ${c.description}` : ""}
                  </p>
                </div>
                <IconButton label={`Sửa ${c.name}`} variant="ghost" size="sm" onClick={() => openEdit(c)}>
                  <Pencil aria-hidden className="size-4" />
                </IconButton>
                <IconButton
                  label={`Xoá ${c.name}`}
                  variant="ghost"
                  size="sm"
                  onClick={() => remove.mutate(c.id)}
                >
                  <Trash2 aria-hidden className="size-4" />
                </IconButton>
              </li>
            ))}
          </ul>
        )}
      </Dialog>

      <Dialog
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? `Sửa ${editing.name}` : "Thêm danh mục"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)}>
              Huỷ
            </Button>
            <Button
              loading={save.isPending}
              disabled={!form.name.trim() || !form.slug.trim()}
              onClick={() => save.mutate()}
            >
              {editing ? "Lưu thay đổi" : "Thêm danh mục"}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <TextField
            label="Tên danh mục"
            required
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <TextField
            label="Mã danh mục"
            required
            placeholder="thuc-an"
            hint="Không dấu, dùng gạch ngang. Mã này đi vào đường dẫn lọc sản phẩm."
            value={form.slug}
            onChange={(e) => setForm({ ...form, slug: e.target.value })}
          />
          <TextField
            label="Mô tả"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>
      </Dialog>
    </>
  );
}
