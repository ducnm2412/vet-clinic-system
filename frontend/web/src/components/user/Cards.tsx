"use client";

import type { ReactNode } from "react";
import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  CalendarDays,
  ChevronRight,
  Clock3,
  Eye,
  Heart,
  MapPin,
  Mars,
  Package,
  Pencil,
  Stethoscope,
  Trash2,
  Venus,
  type LucideIcon,
} from "lucide-react";
import { orderApi, productApi } from "@/lib/api";
import { formatAge, formatDate, formatDateTime, formatPrice, formatTime } from "@/lib/utils/format";
import type { Appointment, OrderSummary, Pet } from "@/types";
import { cn } from "@/lib/utils/cn";
import { PetPhoto } from "./PetPhoto";
import { AppointmentStatusBadge, OrderStatusBadge } from "./StatusBadge";
import { btnOrangeOutline, btnOutline, card, cardHover } from "./ui";

const CLINIC_NAME = "Phòng khám Vet Clinic";

/* ---------------------------------------------------------------- Welcome */

export function WelcomeBanner({ name, children }: { name: string; children?: ReactNode }) {
  return (
    <section className="relative overflow-hidden rounded-[20px] bg-[linear-gradient(100deg,#fff8f0_0%,#ffeedd_55%,#ffdcb8_100%)] px-6 py-8 sm:px-10 sm:py-10">
      <PawMark className="left-[46%] top-5 size-9 rotate-12 text-[#f9a55c]/60" />
      <PawMark className="left-[38%] bottom-4 size-6 -rotate-12 text-[#f9a55c]/45" />
      <div className="relative z-10 max-w-[58%] max-sm:max-w-none">
        <h1 className="text-[24px] font-extrabold leading-tight text-[#1f4a3c] sm:text-[30px]">Xin chào, {name}!</h1>
        <p className="mt-2 flex items-center gap-1.5 text-[15px] text-[#4a3a30]">
          Chào mừng bạn quay trở lại Vet Clinic.
          <Heart aria-hidden className="size-4 fill-[#e5484d] text-[#e5484d]" />
        </p>
        {children}
      </div>
      <div
        className="absolute bottom-0 right-0 hidden h-full w-[46%] sm:block"
        style={{
          maskImage: "linear-gradient(90deg, transparent 0%, #000 28%)",
          WebkitMaskImage: "linear-gradient(90deg, transparent 0%, #000 28%)",
        }}
      >
        <Image src="/img/cho-meo-ngu.png" alt="" fill unoptimized className="object-contain object-right-bottom" />
      </div>
    </section>
  );
}

function PawMark({ className }: { className?: string }) {
  return (
    <svg aria-hidden viewBox="0 0 24 24" fill="currentColor" className={cn("absolute", className)}>
      <ellipse cx="6" cy="9" rx="2.2" ry="2.8" />
      <ellipse cx="10" cy="5.5" rx="2.2" ry="2.8" />
      <ellipse cx="14.5" cy="5.5" rx="2.2" ry="2.8" />
      <ellipse cx="18.5" cy="9" rx="2.2" ry="2.8" />
      <path d="M12.2 11c3.2 0 6 3.4 6 6.2 0 2-1.6 2.8-3.2 2.8-1.2 0-1.9-.6-2.8-.6s-1.6.6-2.8.6C7.8 20 6.2 19.2 6.2 17.2 6.2 14.4 9 11 12.2 11z" />
    </svg>
  );
}

/* ---------------------------------------------------------------- Statistic */

const TONES = {
  orange: { icon: "bg-[#fff1e3] text-[#f57c1f]", bar: "bg-[#f57c1f]" },
  green: { icon: "bg-[#e3f2ec] text-[#1f7a5a]", bar: "bg-[#1f7a5a]" },
  sky: { icon: "bg-[#e8f1fb] text-[#2f6fb2]", bar: "bg-[#2f6fb2]" },
  gold: { icon: "bg-[#fff6d9] text-[#c98a00]", bar: "bg-[#e0a100]" },
} as const;

export function StatisticCard({
  icon: Icon,
  label,
  value,
  unit,
  href,
  tone = "orange",
}: {
  icon: LucideIcon;
  label: string;
  value: ReactNode;
  unit?: string;
  href?: string;
  tone?: keyof typeof TONES;
}) {
  const t = TONES[tone];
  const body = (
    <>
      <span className={cn("grid size-12 place-items-center rounded-2xl", t.icon)}>
        <Icon aria-hidden className="size-6" />
      </span>
      <div className="min-w-0">
        <p className="text-sm text-[#777777]">{label}</p>
        <p className="tnum mt-0.5 text-[26px] font-extrabold leading-none text-[#242424]">
          {value}
          {unit && <span className="ml-1 text-sm font-medium text-[#777777]">{unit}</span>}
        </p>
      </div>
    </>
  );
  const cls = cn(card, "flex items-center gap-4 p-5", href && cardHover);
  return href ? (
    <Link href={href} className={cls}>
      {body}
    </Link>
  ) : (
    <div className={cls}>{body}</div>
  );
}

/* ---------------------------------------------------------------- Pet */

export function PetCard({ pet, onEdit, onDelete }: { pet: Pet; onEdit: () => void; onDelete: () => void }) {
  const male = pet.gender === "MALE";
  const female = pet.gender === "FEMALE";
  return (
    <article className={cn(card, cardHover, "flex flex-col overflow-hidden p-3")}>
      <Link href={`/user/pets/${pet.id}`} className="relative block">
        <PetPhoto photoUrl={pet.photoUrl} species={pet.species} name={pet.name} className="aspect-[4/3] w-full rounded-2xl" />
        <span className="absolute left-3 top-3 rounded-full bg-white/90 px-3 py-1 text-xs font-semibold text-[#1f4a3c] backdrop-blur">
          {pet.species}
        </span>
      </Link>
      <div className="flex flex-1 flex-col px-2 pt-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="truncate text-lg font-bold text-[#1f4a3c]">{pet.name}</h3>
          {(male || female) && (
            <span
              title={male ? "Đực" : "Cái"}
              className={cn(
                "grid size-7 shrink-0 place-items-center rounded-full",
                male ? "bg-[#e8f1fb] text-[#2f6fb2]" : "bg-[#fdecef] text-[#d6457a]",
              )}
            >
              {male ? <Mars aria-hidden className="size-4" /> : <Venus aria-hidden className="size-4" />}
            </span>
          )}
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-x-3 gap-y-2 text-[13px]">
          <div>
            <dt className="text-[#777777]">Giống</dt>
            <dd className="truncate font-medium text-[#242424]">{pet.breed || "Chưa rõ"}</dd>
          </div>
          <div>
            <dt className="text-[#777777]">Tuổi</dt>
            <dd className="font-medium text-[#242424]">{formatAge(pet.dateOfBirth)}</dd>
          </div>
          <div>
            <dt className="text-[#777777]">Ngày sinh</dt>
            <dd className="font-medium text-[#242424]">{pet.dateOfBirth ? formatDate(pet.dateOfBirth) : "Chưa khai"}</dd>
          </div>
          <div>
            <dt className="text-[#777777]">Cân nặng</dt>
            <dd className="tnum font-medium text-[#242424]">{pet.weightKg ? `${pet.weightKg} kg` : "Chưa cân"}</dd>
          </div>
        </dl>
        <div className="mt-auto flex items-center gap-2 pb-1 pt-4">
          <Link href={`/user/pets/${pet.id}`} className={cn(btnOrangeOutline, "h-10 flex-1")}>
            <Eye aria-hidden className="size-4" /> Xem hồ sơ
          </Link>
          <button type="button" onClick={onEdit} aria-label={`Chỉnh sửa ${pet.name}`} className={cn(btnOutline, "w-10 px-0")}>
            <Pencil aria-hidden className="size-4" />
          </button>
          <button
            type="button"
            onClick={onDelete}
            aria-label={`Xoá ${pet.name}`}
            className={cn(btnOutline, "w-10 px-0 hover:border-[#e5484d] hover:text-[#e5484d]")}
          >
            <Trash2 aria-hidden className="size-4" />
          </button>
        </div>
      </div>
    </article>
  );
}

/* ---------------------------------------------------------------- Appointment */

export function AppointmentCard({
  appointment: a,
  pet,
  doctor,
  compact,
}: {
  appointment: Appointment;
  pet: Pet | null | undefined;
  doctor?: string;
  compact?: boolean;
}) {
  const code = `#LH${a.id.replace(/[^a-z0-9]/gi, "").slice(-6).toUpperCase()}`;
  return (
    <article className={cn(card, cardHover, "flex flex-wrap items-center gap-x-5 gap-y-3 p-4")}>
      <PetPhoto photoUrl={pet?.photoUrl} species={pet?.species} name={pet?.name} className="size-20 shrink-0 rounded-2xl" />
      <div className="min-w-52 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-bold text-[#1f4a3c]">{a.serviceName ?? a.reason ?? "Khám bệnh"}</h3>
          {!compact && <span className="tnum text-xs text-[#999999]">{code}</span>}
        </div>
        <p className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px] text-[#555555]">
          <span className="inline-flex items-center gap-1.5">
            <CalendarDays aria-hidden className="size-3.5 text-[#f57c1f]" />
            {formatDate(a.date)}
          </span>
          <span className="tnum inline-flex items-center gap-1.5">
            <Clock3 aria-hidden className="size-3.5 text-[#f57c1f]" />
            {formatTime(a.startTime)}
            {!compact && ` – ${formatTime(a.endTime)}`}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Heart aria-hidden className="size-3.5 text-[#f57c1f]" />
            {pet?.name ?? "Thú cưng đã xoá"}
          </span>
        </p>
        <p className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px] text-[#777777]">
          {!compact && doctor && (
            <span className="inline-flex items-center gap-1.5">
              <Stethoscope aria-hidden className="size-3.5" />
              {doctor}
            </span>
          )}
          <span className="inline-flex items-center gap-1.5">
            <MapPin aria-hidden className="size-3.5" />
            {CLINIC_NAME}
          </span>
        </p>
      </div>
<div className="flex w-full flex-wrap items-center justify-end gap-3 sm:ml-auto sm:w-auto">
      <AppointmentStatusBadge status={a.status} />
      <Link href={`/user/appointments/${a.id}`} className={btnOrangeOutline}>
        Xem chi tiết <ChevronRight aria-hidden className="size-4" />
      </Link>
      </div>
    </article>
  );
}

/* ---------------------------------------------------------------- Order */

/** Ảnh + tên sản phẩm đầu tiên của đơn: tóm tắt đơn không có, nên đọc chi tiết đơn (cache theo id). */
function useOrderPreview(orderId: string) {
  const detail = useQuery({ queryKey: ["orders", orderId], queryFn: () => orderApi.byId(orderId), staleTime: 60_000 });
  const first = detail.data?.items[0];
  const product = useQuery({
    queryKey: ["products", first?.productId],
    queryFn: () => productApi.byId(first!.productId),
    enabled: !!first,
    staleTime: 5 * 60_000,
  });
  return { items: detail.data?.items ?? [], image: product.data?.imageUrl ?? null };
}

export function OrderCard({ order, compact }: { order: OrderSummary; compact?: boolean }) {
  const { items, image } = useOrderPreview(order.id);
  const first = items[0];
  const more = items.length - 1;
  return (
    <article className={cn(card, cardHover, "flex flex-wrap items-center gap-x-5 gap-y-3 p-4")}>
      <span className="relative grid size-20 shrink-0 place-items-center overflow-hidden rounded-2xl border border-[#f4ece4] bg-[#fffaf5]">
        {image ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={image} alt="" className="size-full object-contain p-1.5" />
        ) : (
          <Package aria-hidden className="size-8 text-[#f57c1f]" strokeWidth={1.6} />
        )}
      </span>
      <div className="min-w-52 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-bold text-[#1f4a3c]">{order.orderCode}</h3>
          <span className="text-xs text-[#999999]">{formatDateTime(order.createdAt)}</span>
        </div>
        <p className="mt-1.5 line-clamp-1 text-[14px] text-[#242424]">
          {first ? first.productName : order.totalItems > 0 ? "Đang tải sản phẩm…" : "Tiền khám / thuốc"}
        </p>
        {!compact && (
          <p className="mt-0.5 text-[13px] text-[#777777]">
            {order.totalItems > 0 ? `${order.totalItems} sản phẩm` : "Hoá đơn khám"}
            {more > 0 && ` · và ${more} sản phẩm khác`}
          </p>
        )}
      </div>
<div className="flex w-full flex-wrap items-center justify-end gap-3 sm:ml-auto sm:w-auto">
      <OrderStatusBadge order={order} />
      <p className="tnum text-right text-[17px] font-extrabold text-[#242424]">{formatPrice(order.total)}</p>
      <Link href={`/user/orders/${order.id}`} className={btnOrangeOutline}>
        Chi tiết <ChevronRight aria-hidden className="size-4" />
      </Link>
      </div>
    </article>
  );
}
