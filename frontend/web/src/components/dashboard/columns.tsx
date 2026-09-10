import { APPOINTMENT_STATUS, ORDER_STATUS } from "@/lib/utils/status";
import { formatDate, formatDateTime, formatPrice, formatTime } from "@/lib/utils/format";
import { StatusTag, type Column } from "@/components/ui";
import type { Appointment, OrderSummary } from "@/types";

/**
 * Cột bảng dùng chung cho các trang tổng quan. Bốn vai trò nhìn cùng dữ liệu nhưng cần
 * khác nhau vài cột, nên khác biệt để thành tham số thay vì chép bảng ra nhiều nơi —
 * sửa nhãn hay định dạng một lần là mọi trang theo.
 */

export function appointmentColumns(
  options: { showDate?: boolean } = {},
): Column<Appointment>[] {
  return [
    {
      key: "when",
      header: options.showDate ? "Thời gian" : "Giờ",
      cell: (a) =>
        options.showDate ? (
          <span className="whitespace-nowrap">
            {formatDate(a.date)} lúc <span className="tnum">{formatTime(a.startTime)}</span>
          </span>
        ) : (
          <span className="tnum font-medium text-ink">{formatTime(a.startTime)}</span>
        ),
    },
    {
      key: "status",
      header: "Trạng thái",
      cell: (a) => <StatusTag status={APPOINTMENT_STATUS[a.status]} />,
    },
    {
      key: "reason",
      header: "Lý do khám",
      hideBelow: "lg",
      cell: (a) => <span className="text-ink-soft">{a.reason || "Chủ nuôi không ghi"}</span>,
    },
  ];
}

export function orderColumns(
  options: { showRecipient?: boolean; showCreated?: boolean } = {},
): Column<OrderSummary>[] {
  const cols: Column<OrderSummary>[] = [
    {
      key: "code",
      header: "Mã đơn",
      cell: (o) => <span className="font-medium text-ink">{o.orderCode}</span>,
    },
  ];

  if (options.showRecipient) {
    cols.push({ key: "recipient", header: "Người nhận", cell: (o) => o.recipientName });
  }

  if (options.showCreated) {
    cols.push({
      key: "created",
      header: "Đặt lúc",
      hideBelow: "lg",
      cell: (o) => <span className="text-bark">{formatDateTime(o.createdAt)}</span>,
    });
  }

  cols.push(
    { key: "status", header: "Trạng thái", cell: (o) => <StatusTag status={ORDER_STATUS[o.status]} /> },
    { key: "total", header: "Tổng tiền", numeric: true, cell: (o) => formatPrice(o.total) },
  );

  return cols;
}
