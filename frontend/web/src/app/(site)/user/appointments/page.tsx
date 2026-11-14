"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { CalendarCheck2, CalendarClock, CalendarDays, CalendarPlus, CalendarX2 } from "lucide-react";
import { bookingApi, myPetApi } from "@/lib/api";
import { todayISO } from "@/lib/utils/format";
import { useDoctorDirectory } from "@/lib/useDoctors";
import type { Appointment } from "@/types";
import { AppointmentCard, StatisticCard } from "@/components/user/Cards";
import { FilterDropdown, SearchInput } from "@/components/user/Controls";
import { EmptyState, ErrorState, SkeletonList } from "@/components/user/EmptyState";
import { PageHeader } from "@/components/user/PageHeader";
import { Tabs } from "@/components/user/Tabs";
import { btnPrimary } from "@/components/user/ui";

type Tab = "ALL" | "UPCOMING" | "DONE" | "CANCELLED";
type Range = "ALL" | "30" | "90" | "365";

export default function MyAppointmentsPage() {
  const [tab, setTab] = useState<Tab>("ALL");
  const [range, setRange] = useState<Range>("ALL");
  const [q, setQ] = useState("");
  const appointments = useQuery({ queryKey: ["appointments", "mine"], queryFn: bookingApi.mine });
  // Lịch hẹn chỉ mang petId — tra tên/ảnh bé từ danh sách thú cưng của chính khách.
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: myPetApi.list });
  const doctors = useDoctorDirectory();

  const petById = new Map((pets.data ?? []).map((p) => [p.id, p]));
  const today = todayISO();
  const all = appointments.data ?? [];
  const isUpcoming = (a: Appointment) => a.date >= today && (a.status === "PENDING" || a.status === "CONFIRMED");
  const isCancelled = (a: Appointment) => a.status === "CANCELLED" || a.status === "NO_SHOW";
  const upcoming = all.filter(isUpcoming);
  const done = all.filter((a) => a.status === "COMPLETED");
  const cancelled = all.filter(isCancelled);

  const since = (() => {
    if (range === "ALL") return "";
    const d = new Date(`${today}T00:00:00`);
    d.setDate(d.getDate() - Number(range));
    return d.toISOString().slice(0, 10);
  })();
  const base = tab === "UPCOMING" ? upcoming : tab === "DONE" ? done : tab === "CANCELLED" ? cancelled : all;
  const list = base
    .filter((a) => !since || a.date >= since || isUpcoming(a))
    .filter((a) => {
      const text = `${a.serviceName ?? ""} ${a.reason ?? ""} ${petById.get(a.petId)?.name ?? ""}`.toLowerCase();
      return text.includes(q.trim().toLowerCase());
    })
    .sort((a, b) =>
      isUpcoming(a) && isUpcoming(b)
        ? `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`)
        : isUpcoming(a) !== isUpcoming(b)
          ? isUpcoming(a) ? -1 : 1
          : `${b.date}${b.startTime}`.localeCompare(`${a.date}${a.startTime}`),
    );

  return (
    <>
      <PageHeader
        title="Lịch hẹn của tôi"
        crumbs={[{ label: "Lịch hẹn của tôi" }]}
        action={
          <Link href="/appointments/create" className={btnPrimary}>
            <CalendarPlus aria-hidden className="size-4" /> Đặt lịch khám
          </Link>
        }
      />

      <section aria-label="Thống kê lịch hẹn" className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatisticCard icon={CalendarClock} label="Sắp diễn ra" value={upcoming.length} tone="orange" />
        <StatisticCard icon={CalendarCheck2} label="Đã hoàn thành" value={done.length} tone="green" />
        <StatisticCard icon={CalendarX2} label="Đã huỷ / vắng" value={cancelled.length} tone="sky" />
      </section>

      <div className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <Tabs<Tab>
          tabs={[
            { key: "ALL", label: "Tất cả", count: all.length },
            { key: "UPCOMING", label: "Sắp diễn ra", count: upcoming.length },
            { key: "DONE", label: "Đã hoàn thành", count: done.length },
            { key: "CANCELLED", label: "Đã huỷ", count: cancelled.length },
          ]}
          value={tab}
          onChange={setTab}
        />
        <div className="flex flex-col gap-2 sm:flex-row">
          <SearchInput value={q} onChange={setQ} placeholder="Tìm dịch vụ, tên bé…" className="sm:w-56" />
          <FilterDropdown<Range>
            label="Khoảng thời gian"
            value={range}
            onChange={setRange}
            className="sm:w-40"
            options={[
              { value: "ALL", label: "Mọi thời gian" },
              { value: "30", label: "30 ngày qua" },
              { value: "90", label: "3 tháng qua" },
              { value: "365", label: "12 tháng qua" },
            ]}
          />
        </div>
      </div>

      {appointments.isLoading ? (
        <SkeletonList count={3} className="h-28" />
      ) : appointments.isError ? (
        <ErrorState title="Chưa mở được lịch khám" onRetry={() => appointments.refetch()} />
      ) : list.length === 0 ? (
        <EmptyState
          icon={CalendarDays}
          title={all.length === 0 ? "Chưa có lịch khám nào" : "Không có lịch hẹn phù hợp"}
          description={all.length === 0 ? "Đặt lịch trước thì đến nơi là được khám luôn." : "Thử đổi tab hoặc bộ lọc."}
          action={
            all.length === 0 ? (
              <Link href="/appointments/create" className={btnPrimary}>
                Đặt lịch khám
              </Link>
            ) : undefined
          }
        />
      ) : (
        <ul className="space-y-3">
          {list.map((a) => (
            <li key={a.id}>
              <AppointmentCard appointment={a} pet={petById.get(a.petId)} doctor={doctors.label(a.doctorUserId)} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
