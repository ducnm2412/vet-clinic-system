"use client";

import { useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import { Camera } from "lucide-react";
import { ApiError } from "@/lib/api";
import { Button, useToast } from "@/components/ui";
import { SiteButton } from "@/components/site/primitives";

// Khớp giới hạn của profile-service (PhotoStorageService): chặn sớm để khỏi tải cả file lớn rồi mới bị từ chối.
const MAX_BYTES = 5 * 1024 * 1024;
const ACCEPT = "image/jpeg,image/png,image/webp";

/**
 * Nút chọn ảnh từ máy rồi tải lên ngay. Dùng chung cho thú cưng, bác sĩ, nhân viên: chỗ gọi truyền
 * hàm `upload` tương ứng và tự làm mới dữ liệu trong `onUploaded`.
 * `variant` chọn kiểu nút cho khớp phần trang: "site" cho trang khách, "dashboard" cho trang nội bộ.
 */
export function PhotoUploadButton({
  upload,
  onUploaded,
  variant,
  label = "Đổi ảnh",
}: {
  upload: (file: File) => Promise<unknown>;
  onUploaded?: () => void;
  variant: "site" | "dashboard";
  label?: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  const toast = useToast();

  const send = useMutation({
    mutationFn: upload,
    onSuccess: () => {
      toast.success("Đã cập nhật ảnh");
      onUploaded?.();
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không tải được ảnh lên."),
  });

  function onPick(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    // Xoá giá trị để chọn lại đúng file vừa rồi vẫn kích hoạt onChange.
    e.target.value = "";
    if (!file) return;

    if (!ACCEPT.split(",").includes(file.type)) {
      toast.error("Chỉ nhận ảnh JPG, PNG hoặc WebP");
      return;
    }
    if (file.size > MAX_BYTES) {
      toast.error("Ảnh quá lớn, tối đa 5MB");
      return;
    }
    send.mutate(file);
  }

  const open = () => input.current?.click();

  return (
    <>
      <input ref={input} type="file" accept={ACCEPT} hidden onChange={onPick} />
      {variant === "site" ? (
        <SiteButton type="button" variant="outline" loading={send.isPending} onClick={open}>
          <Camera aria-hidden className="size-4" />
          {label}
        </SiteButton>
      ) : (
        <Button type="button" size="sm" variant="secondary" loading={send.isPending} onClick={open}>
          <Camera aria-hidden className="size-4" />
          {label}
        </Button>
      )}
    </>
  );
}
