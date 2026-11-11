import { APPOINTMENT_LOOK, orderLook, StatusPill } from "@/components/site/StatusPill";
import type { AppointmentStatus, OrderSummary } from "@/types";

/** Nhãn trạng thái — bọc StatusPill có sẵn để màu trạng thái thống nhất toàn site. */
export function AppointmentStatusBadge({ status }: { status: AppointmentStatus }) {
  return <StatusPill look={APPOINTMENT_LOOK[status]} />;
}

export function OrderStatusBadge({
  order,
}: {
  order: Pick<OrderSummary, "status" | "channel" | "paymentStatus" | "paymentMethod" | "refundRequired">;
}) {
  return <StatusPill look={orderLook(order)} />;
}
