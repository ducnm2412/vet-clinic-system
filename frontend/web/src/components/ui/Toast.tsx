"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

type ToastKind = "success" | "error";

interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastApi {
  /** Báo việc đã xong. Dùng thể hoàn thành: "Đã lưu bệnh án". */
  success: (message: string) => void;
  error: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);

  const remove = useCallback((id: number) => {
    setItems((list) => list.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string) => {
      const id = nextId++;
      setItems((list) => [...list, { id, kind, message }]);
      // Lỗi ở lâu hơn vì người dùng thường cần đọc kỹ rồi mới xử lý.
      window.setTimeout(() => remove(id), kind === "error" ? 7000 : 4000);
    },
    [remove],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (m) => push("success", m),
      error: (m) => push("error", m),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:items-end"
      >
        {items.map((t) => (
          <div
            key={t.id}
            className={cn(
              "pointer-events-auto flex w-full max-w-sm items-start gap-2.5 rounded-[var(--radius-control)] border px-4 py-3 text-sm shadow-[var(--shadow-dropdown)]",
              t.kind === "success"
                ? "border-moss/30 bg-moss-wash text-ink"
                : "border-danger/30 bg-danger-wash text-ink",
            )}
          >
            {t.kind === "success" && <Check aria-hidden className="mt-0.5 size-4 text-moss" />}
            <p className="min-w-0 flex-1">{t.message}</p>
            <button
              onClick={() => remove(t.id)}
              aria-label="Đóng thông báo"
              className="-m-1 rounded p-1 text-bark hover:text-ink"
            >
              <X aria-hidden className="size-3.5" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast phải nằm trong <ToastProvider>");
  return ctx;
}
