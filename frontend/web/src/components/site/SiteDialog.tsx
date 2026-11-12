"use client";

import { useCallback, useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";

/**
 * Hộp thoại của website khách hàng. Dựng trên <dialog> gốc như bên dashboard — trình
 * duyệt tự giữ focus bên trong, tự đóng bằng Esc — nhưng mặc nền tròn hơn và thoáng hơn
 * cho hợp với mặt tiền.
 */
export function SiteDialog({
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
        if (e.target === ref.current) onClose();
      }}
      aria-labelledby="site-dialog-title"
      className="m-auto max-h-[calc(100dvh-1.5rem)] w-[min(34rem,calc(100vw-1.5rem))] flex-col overflow-hidden rounded-3xl border open:flex border-mist bg-white p-0 text-pine shadow-[var(--shadow-lift)] backdrop:bg-pine/40 backdrop:backdrop-blur-[2px]"
    >
      <div className="flex shrink-0 items-start gap-4 px-4 pb-1 pt-4 sm:px-6 sm:pb-2 sm:pt-6">
        <div className="min-w-0 flex-1">
          <h2 id="site-dialog-title" className="t-h3 text-[20px] sm:text-[inherit]">
            {title}
          </h2>
          {description && <p className="mt-1 text-sm text-stone sm:mt-1.5 sm:text-base">{description}</p>}
        </div>
        <button
          onClick={onClose}
          aria-label="Đóng"
          className="-mr-2 -mt-2 grid size-10 shrink-0 place-items-center rounded-full text-stone transition-colors hover:bg-mint hover:text-pine"
        >
          <X aria-hidden className="size-5" />
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3 sm:px-6 sm:py-4">{children}</div>

      {footer && (
        <div className="flex shrink-0 justify-end gap-3 border-t border-mist bg-white px-4 py-3 sm:px-6 sm:py-4 [&>*]:flex-1 [&>*]:whitespace-nowrap max-sm:[&>*]:px-3 max-sm:[&>*]:text-[15px] sm:[&>*]:flex-none">
          {footer}
        </div>
      )}
    </dialog>
  );
}
