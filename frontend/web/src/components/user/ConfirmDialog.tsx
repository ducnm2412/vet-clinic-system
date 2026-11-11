"use client";

import type { ReactNode } from "react";
import { SiteButton } from "@/components/site/primitives";
import { SiteDialog } from "@/components/site/SiteDialog";

/** Hộp xác nhận cho thao tác không hoàn tác được (xoá thú cưng, huỷ lịch, huỷ đơn…). */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "Xác nhận",
  cancelLabel = "Giữ lại",
  loading,
  onConfirm,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
  children?: ReactNode;
}) {
  return (
    <SiteDialog
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      footer={
        <>
          <SiteButton variant="outline" onClick={onClose}>
            {cancelLabel}
          </SiteButton>
          <SiteButton loading={loading} onClick={onConfirm}>
            {confirmLabel}
          </SiteButton>
        </>
      }
    >
      {children}
    </SiteDialog>
  );
}
