"use client";

import { useCallback, useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";
import { IconButton } from "./Button";

/**
 * Hộp thoại dựng trên <dialog> gốc: trình duyệt lo giữ focus bên trong, đóng bằng Esc và
 * lớp phủ chặn tương tác — không cần thư viện ngoài cho một hành vi đã có sẵn.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  footer,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) el.showModal();
    if (!open && el.open) el.close();
  }, [open]);

  // Esc kích hoạt sự kiện "cancel" của <dialog>; chặn mặc định để state cha là nguồn duy nhất.
  const onCancel = useCallback(
    (e: React.SyntheticEvent<HTMLDialogElement>) => {
      e.preventDefault();
      onClose();
    },
    [onClose],
  );

  return (
    <dialog
      ref={ref}
      onCancel={onCancel}
      onClick={(e) => {
        // Bấm ra ngoài phần nội dung thì đóng.
        if (e.target === ref.current) onClose();
      }}
      aria-labelledby="dialog-title"
      className="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-[var(--radius-overlay)] border border-line bg-surface p-0 text-ink shadow-[var(--shadow-dialog)] backdrop:bg-ink/30"
    >
      <div className="flex items-start gap-4 border-b border-line px-5 py-4">
        <div className="min-w-0 flex-1">
          <h2 id="dialog-title" className="font-medium">
            {title}
          </h2>
          {description && <p className="mt-0.5 text-sm text-bark">{description}</p>}
        </div>
        <IconButton label="Đóng" variant="ghost" size="sm" onClick={onClose}>
          <X aria-hidden className="size-4" />
        </IconButton>
      </div>

      <div className="px-5 py-4">{children}</div>

      {footer && (
        <div className="flex justify-end gap-2 border-t border-line bg-paper px-5 py-3">
          {footer}
        </div>
      )}
    </dialog>
  );
}
