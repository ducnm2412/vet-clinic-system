"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { CalendarDays, Clock3, MapPin } from "lucide-react";
import { bookingApi, customerApi } from "@/lib/api";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import { CLINIC } from "@/config/clinic";
import type { Appointment, Pet } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { APPOINTMENT_LOOK, StatusPill } from "@/components/site/StatusPill";

export default function AppointmentsPage() {
  return (
    <CustomerOnly>
      <AppointmentsBody />
    </CustomerOnly>
  );
}

function AppointmentsBody() {
  const appointments = useQuery({ queryKey: ["appointments", "mine"], queryFn: bookingApi.mine });
  // Lịch hẹn chỉ mang `petId`, nên phải tra tên bé từ danh sách thú cưng của chính khách.
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: customerApi.pets });

  const petById = new Map((pets.data ?? []).map((p) => [p.id, p]));
  const today = todayISO();

  const all = [...(appointments.data ?? [])];
  const upcoming = all
    .filter((a) => a.date >= today && (a.status === "PENDING" || a.status === "CONFIRMED"))
    .sort((a, b) => `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`));
  const past = all
    .filter((a) => !upcoming.includes(a))
    .sort((a, b) => `${b.date}${b.startTime}`.localeCompare(`${a.date}${a.startTime}`));

  return (
    <>
      <div className="bg-mint py-14 md:py-20">
        <Container>
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div>
              <h1 className="t-h2">Lịch khám của bạn</h1>
              <p className="measure t-lede mt-4 text-stone">
                Sắp tới bé nào phải đến phòng khám, và những lần đã khám xong.
              </p>
            </div>
            <ButtonLink href="/appointments/create">Đặt lịch mới</ButtonLink>
          </div>
        </Container>
      </div>

      <Container className="py-12 md:py-16">
        {appointments.isLoading ? (
          <div className="space-y-4" aria-hidden>
            <div className="h-48 animate-pulse rounded-[var(--radius-card)] bg-mint" />
            <div className="h-20 animate-pulse rounded-[var(--radius-card)] bg-mint" />
          </div>
        ) : appointments.isError ? (
          <div className="rounded-[var(--radius-card)] border border-mist px-6 py-14 text-center">
            <p className="t-h3">Chưa mở được lịch khám</p>
            <p className="mt-2 text-stone">Kết nối tới phòng khám đang trục trặc.</p>
            <SiteButton
              variant="outline"
              className="mt-6"
              onClick={() => appointments.refetch()}
            >
              Thử lại
            </SiteButton>
          </div>
        ) : all.length === 0 ? (
          <EmptyAppointments />
        ) : (
          <>
            {upcoming.length > 0 && (
              <section>
                <h2 className="t-h3">Sắp tới</h2>
                <ul className="mt-6 space-y-5">
                  {upcoming.map((a) => (
                    <li key={a.id}>
                      <UpcomingCard appointment={a} pet={petById.get(a.petId) ?? null} />
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {past.length > 0 && (
              <section className={upcoming.length > 0 ? "mt-16" : ""}>
                <h2 className="t-h3">Đã qua</h2>
                <ul className="mt-6 divide-y divide-mist border-y border-mist">
                  {past.map((a) => (
                    <PastRow key={a.id} appointment={a} pet={petById.get(a.petId) ?? null} />
                  ))}
                </ul>
              </section>
            )}
          </>
        )}
      </Container>
    </>
  );
}

/**
 * Lịch sắp tới là thứ khách vào trang này để tìm, nên nó được một tấm thẻ lớn với đủ
 * thông tin cần khi ra khỏi nhà: bé nào, mấy giờ, ở đâu.
 */
function UpcomingCard({ appointment: a, pet }: { appointment: Appointment; pet: Pet | null }) {
  return (
    <Link
      href={`/appointments/${a.id}`}
      className="block overflow-hidden rounded-[var(--radius-card)] border border-mist transition-[border-color,transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-teal hover:shadow-[var(--shadow-lift)]"
    >
      <div className="flex flex-wrap gap-6 p-6 md:gap-8">
        <PetAvatar species={pet?.species} small className="h-24 w-20" />

        <div className="min-w-52 flex-1">
          <StatusPill look={APPOINTMENT_LOOK[a.status]} />
          <h3 className="t-h3 mt-3">{pet?.name ?? "Thú cưng đã xoá hồ sơ"}</h3>
          <p className="measure mt-2 text-stone">
            {a.reason?.trim() || "Bạn chưa ghi lý do khám."}
          </p>
        </div>

        <dl className="min-w-52 space-y-3 rounded-[var(--radius-card)] bg-mint p-5 text-[15px]">
          <div className="flex gap-3">
            <CalendarDays aria-hidden className="mt-0.5 size-4 shrink-0 text-teal" />
            <div>
              <dt className="text-stone">Ngày khám</dt>
              <dd className="font-medium">{formatDate(a.date)}</dd>
            </div>
          </div>
          <div className="flex gap-3">
            <Clock3 aria-hidden className="mt-0.5 size-4 shrink-0 text-teal" />
            <div>
              <dt className="text-stone">Giờ</dt>
              <dd className="tnum font-medium">
                {formatTime(a.startTime)} đến {formatTime(a.endTime)}
              </dd>
            </div>
          </div>
          <div className="flex gap-3">
            <MapPin aria-hidden className="mt-0.5 size-4 shrink-0 text-teal" />
            <div>
              <dt className="text-stone">Tại</dt>
              <dd className="font-medium">{CLINIC.address}</dd>
            </div>
          </div>
        </dl>
      </div>
    </Link>
  );
}

function PastRow({ appointment: a, pet }: { appointment: Appointment; pet: Pet | null }) {
  return (
    <li>
      <Link
        href={`/appointments/${a.id}`}
        className="flex flex-wrap items-center gap-x-6 gap-y-3 py-5 transition-colors hover:bg-mint/60"
      >
        <PetAvatar species={pet?.species} small className="h-14 w-11" />
        <div className="min-w-36">
          <p className="font-medium">{pet?.name ?? "Không rõ bé nào"}</p>
          <p className="text-[15px] text-stone">{formatDate(a.date)}</p>
        </div>
        <p className="tnum min-w-28 text-[15px] text-stone">
          {formatTime(a.startTime)} đến {formatTime(a.endTime)}
        </p>
        <p className="min-w-0 flex-1 truncate text-stone">
          {a.reason?.trim() || "Không ghi lý do khám"}
        </p>
        <StatusPill look={APPOINTMENT_LOOK[a.status]} />
      </Link>
    </li>
  );
}

function EmptyAppointments() {
  return (
    <div className="mx-auto max-w-lg py-10 text-center">
      <span aria-hidden className="arch mx-auto grid h-48 w-36 place-items-center bg-mint text-teal">
        <CalendarDays className="size-14" strokeWidth={1.5} />
      </span>
      <h2 className="t-h2 mt-10">Chưa có lịch khám nào</h2>
      <p className="mt-4 text-stone">
        Đặt lịch trước thì đến nơi là được khám luôn, không phải ngồi chờ đến lượt.
      </p>
      <div className="mt-8">
        <ButtonLink href="/appointments/create" size="lg">
          Đặt lịch khám
        </ButtonLink>
      </div>
    </div>
  );
}
