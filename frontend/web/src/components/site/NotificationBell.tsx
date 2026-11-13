"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CalendarCheck, CalendarClock, CalendarX, Send } from "lucide-react";
import { notificationApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils/cn";
import type { AppNotification } from "@/types";

const QUERY_KEY = ["notifications"] as const;

/** Hỏi lại máy chủ mỗi 30 giây: thông báo mới (admin vừa xếp lịch) hiện lên mà không cần tải lại trang. */
const POLL_MS = 30_000;

/** Chuông thông báo của khách: chấm số chưa đọc, bấm mở danh sách, bấm một mục để tới trang liên quan. */
export function NotificationBell() {
  const { email, hasRole } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  // Chỉ khách hàng nhận thông báo (backend lưu theo userId của khách). Nhân viên không gọi để
  // khỏi nhận lỗi vô cớ.
  const enabled = Boolean(email) && hasRole("CUSTOMER");

  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: notificationApi.list,
    enabled,
    refetchInterval: POLL_MS,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: QUERY_KEY });
  const markRead = useMutation({ mutationFn: notificationApi.markRead, onSuccess: refresh });
  const markAllRead = useMutation({ mutationFn: notificationApi.markAllRead, onSuccess: refresh });

  // Esc đóng danh sách.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  if (!enabled) return null;

  const items = query.data?.items ?? [];
  const unread = query.data?.unreadCount ?? 0;

  function openItem(n: AppNotification) {
    if (!n.read) markRead.mutate(n.id);
    setOpen(false);
    if (n.link) router.push(n.link);
  }

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={unread > 0 ? `Thông báo, ${unread} chưa đọc` : "Thông báo"}
        className="relative grid size-11 place-items-center rounded-full text-pine transition-colors hover:bg-mint"
      >
        <Bell aria-hidden className="size-5" />
        {unread > 0 && (
          <span className="tnum absolute right-1 top-1 grid min-w-5 place-items-center rounded-full bg-coral-deep px-1 text-[11px] font-semibold leading-5 text-white">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>

      {open && (
        <>
          <button
            aria-label="Đóng thông báo"
            className="fixed inset-0 z-10 cursor-default"
            onClick={() => setOpen(false)}
          />
          {/*
            Điện thoại: bảng trải gần hết bề ngang màn hình, ngay dưới thanh đầu trang — chuông
            nằm giữa thanh nên neo theo chuông sẽ tràn ra ngoài mép trái.
          */}
          <div
            role="dialog"
            aria-label="Thông báo"
            className="fixed inset-x-4 top-[4.5rem] z-20 flex max-h-[min(32rem,calc(100vh-6rem))] animate-[dropIn_.18s_ease-out] flex-col overflow-hidden rounded-2xl border border-mist bg-white shadow-[var(--shadow-lift)] sm:absolute sm:inset-x-auto sm:right-0 sm:top-auto sm:mt-2 sm:w-[24rem] sm:origin-top-right"
          >
            <div className="flex items-center justify-between gap-3 border-b border-mist px-4 py-3">
              <h2 className="font-display text-lg text-pine">Thông báo</h2>
              {unread > 0 && (
                <button
                  onClick={() => markAllRead.mutate()}
                  disabled={markAllRead.isPending}
                  className="text-sm text-teal underline-offset-2 hover:underline disabled:opacity-60"
                >
                  Đánh dấu đã đọc hết
                </button>
              )}
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto">
              {query.isError ? (
                <p className="px-4 py-8 text-center text-sm text-stone">
                  Không tải được thông báo.{" "}
                  <button onClick={() => query.refetch()} className="text-teal underline">
                    Thử lại
                  </button>
                </p>
              ) : query.isLoading ? (
                <p className="px-4 py-8 text-center text-sm text-stone">Đang tải…</p>
              ) : items.length === 0 ? (
                <p className="px-4 py-10 text-center text-sm text-stone">
                  Chưa có thông báo nào. Khi phòng khám xếp lịch hoặc đề xuất giờ khác cho bạn, thông báo sẽ hiện ở đây.
                </p>
              ) : (
                <ul>
                  {items.map((n) => (
                    <li key={n.id} className="border-b border-mist last:border-b-0">
                      <button
                        onClick={() => openItem(n)}
                        className={cn(
                          "flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-mint",
                          !n.read && "bg-mint/50",
                        )}
                      >
                        <TypeIcon type={n.type} />
                        <span className="min-w-0 flex-1">
                          <span className={cn("block text-[15px] text-pine", !n.read && "font-semibold")}>
                            {n.title}
                          </span>
                          <span className="mt-0.5 block text-sm leading-snug text-stone">{n.message}</span>
                          <span className="mt-1 block text-xs text-stone/80">{timeAgo(n.createdAt)}</span>
                        </span>
                        {!n.read && (
                          <span aria-label="Chưa đọc" className="mt-2 size-2 shrink-0 rounded-full bg-coral-deep" />
                        )}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

/** Đề xuất giờ khác cần khách trả lời nên nổi màu cam; các loại còn lại màu dịu. */
function TypeIcon({ type }: { type: string }) {
  const Icon =
    type === "CONFIRMED" ? CalendarCheck : type === "PROPOSAL" ? CalendarClock : type === "CANCELLED_BY_CLINIC" ? CalendarX : type === "REQUEST_RECEIVED" ? Send : Bell;
  const tone =
    type === "PROPOSAL"
      ? "bg-[#fff1e6] text-[#c2570c]"
      : type === "CANCELLED_BY_CLINIC"
        ? "bg-[#fdeceb] text-coral-deep"
        : "bg-mint text-teal";
  return (
    <span className={cn("grid size-9 shrink-0 place-items-center rounded-full", tone)}>
      <Icon aria-hidden className="size-[18px]" />
    </span>
  );
}

/** "vừa xong", "5 phút trước", "3 giờ trước", "2 ngày trước", quá một tuần thì hiện ngày. */
function timeAgo(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const minutes = Math.floor((Date.now() - then) / 60_000);
  if (minutes < 1) return "vừa xong";
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} ngày trước`;
  return new Date(iso).toLocaleDateString("vi-VN");
}
