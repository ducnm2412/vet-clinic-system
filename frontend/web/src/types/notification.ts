/** Thông báo trong app (cái chuông ở đầu trang) do notification-service lưu. */
export interface AppNotification {
  id: string;
  /** Loại sự kiện gốc: REQUEST_RECEIVED, CONFIRMED, PROPOSAL, CANCELLED_BY_CLINIC... */
  type: string;
  title: string;
  message: string;
  /** Đường dẫn tương đối trong web, vd /appointments/{id}; null nếu không có trang đích. */
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationList {
  items: AppNotification[];
  /** Tổng số chưa đọc, kể cả những thông báo cũ không nằm trong `items`. */
  unreadCount: number;
}
