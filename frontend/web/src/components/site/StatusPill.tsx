import { cn } from "@/lib/utils/cn";
import type { AppointmentStatus, OrderStatus, PrescriptionStatus } from "@/types";

/*
  Nhãn trạng thái nói bằng lời của khách hàng, không bằng lời của hệ thống.
  Bên dashboard "PENDING" của đơn hàng là "Chờ xác nhận" — đúng với người trực quầy.
  Ở đây khách cần biết mình phải làm gì tiếp, nên chữ khác đi một chút.

  Luôn có cả chấm màu lẫn chữ: ai không phân biệt được màu thì chữ vẫn nói đủ.
*/

interface Look {
  label: string;
  dot: string;
  skin: string;
}

const WAIT = "bg-[#f2a93b]/14 text-[#8a5a0f]";
const GOING = "bg-teal/12 text-teal-deep";
const DONE = "bg-pine/8 text-pine";
const STOPPED = "bg-coral/12 text-coral-deep";

export const APPOINTMENT_LOOK: Record<AppointmentStatus, Look> = {
  PENDING: { label: "Chờ phòng khám xác nhận", dot: "bg-[#f2a93b]", skin: WAIT },
  CONFIRMED: { label: "Đã xác nhận", dot: "bg-teal", skin: GOING },
  COMPLETED: { label: "Đã khám xong", dot: "bg-pine", skin: DONE },
  CANCELLED: { label: "Đã huỷ", dot: "bg-coral", skin: STOPPED },
  NO_SHOW: { label: "Bạn không đến", dot: "bg-stone", skin: DONE },
};

export const ORDER_LOOK: Record<OrderStatus, Look> = {
  PENDING: { label: "Chờ phòng khám xác nhận", dot: "bg-[#f2a93b]", skin: WAIT },
  CONFIRMED: { label: "Đã xác nhận", dot: "bg-teal", skin: GOING },
  SHIPPING: { label: "Đang trên đường giao", dot: "bg-teal", skin: GOING },
  COMPLETED: { label: "Đã giao xong", dot: "bg-pine", skin: DONE },
  CANCELLED: { label: "Đã huỷ", dot: "bg-coral", skin: STOPPED },
};

export const PRESCRIPTION_LOOK: Record<PrescriptionStatus, Look> = {
  PENDING: { label: "Chờ bạn trả tiền tại quầy", dot: "bg-[#f2a93b]", skin: WAIT },
  PAID: { label: "Đã trả tiền, chờ lấy thuốc", dot: "bg-teal", skin: GOING },
  RECEIVED: { label: "Bạn đã nhận thuốc", dot: "bg-pine", skin: DONE },
};

export function StatusPill({ look, className }: { look: Look; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-[14px] font-medium",
        look.skin,
        className,
      )}
    >
      <span aria-hidden className={cn("size-2 shrink-0 rounded-full", look.dot)} />
      {look.label}
    </span>
  );
}
