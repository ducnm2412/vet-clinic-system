package com.vetclinic.notification.dto;

import java.util.List;

/** Các thông báo mới nhất kèm tổng số chưa đọc (đếm cả những cái không nằm trong danh sách). */
public record NotificationListResponse(List<NotificationResponse> items, long unreadCount) {
}
