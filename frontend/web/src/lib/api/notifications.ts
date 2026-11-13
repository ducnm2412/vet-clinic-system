import { http } from "./client";
import type { AppNotification, NotificationList } from "@/types";

export const notificationApi = {
  /** Thông báo mới nhất của người đang đăng nhập, kèm số chưa đọc. */
  list: () => http.get<NotificationList>("/notifications"),

  markRead: (id: string) => http.put<AppNotification>(`/notifications/${id}/read`),

  markAllRead: () => http.put<void>("/notifications/read-all"),
};
