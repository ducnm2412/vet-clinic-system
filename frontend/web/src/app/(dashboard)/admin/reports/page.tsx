"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Ban, CalendarCheck, Receipt, Wallet } from "lucide-react";
import { ApiError, reportingApi, type ReportRange } from "@/lib/api";
import { formatPrice, todayISO } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Metric, MetricRow } from "@/components/dashboard/Metric";
import { AppointmentChart } from "@/components/reporting/AppointmentChart";
import { RevenueChart } from "@/components/reporting/RevenueChart";
import { UnavailableNote } from "@/components/reporting/UnavailableNote";
import { daysBetween, percent, shiftDays } from "@/components/reporting/format";
import {
  DataTable,
  EmptyState,
  ErrorState,
  Skeleton,
  TableFrame,
  controlClass,
  type Column,
} from "@/components/ui";
import type { AppointmentReport, ReportInterval } from "@/types";

/** Backend chỉ cho xem theo ngày tối đa 92 ngày, và mọi khoảng tối đa 731 ngày. */
const MAX_DAYS_DAILY = 92;
const MAX_DAYS = 731;

const PRESETS = [
  { days: 7, label: "7 ngày" },
  { days: 30, label: "30 ngày" },
  { days: 90, label: "90 ngày" },
  { days: 365, label: "12 tháng" },
] as const;

const INTERVALS: { value: ReportInterval; label: string }[] = [
  { value: "DAY", label: "Theo ngày" },
  { value: "MONTH", label: "Theo tháng" },
  { value: "QUARTER", label: "Theo quý" },
];

type DoctorRow = AppointmentReport["byDoctor"][number];

export default function ReportsPage() {
  const today = todayISO();
  const [range, setRange] = useState<ReportRange>({ from: shiftDays(today, -29), to: today });
  const [interval, setGrouping] = useState<ReportInterval>("DAY");

  const days = daysBetween(range.from, range.to);
  const rangeError =
    !range.from || !range.to
      ? "Chọn đủ ngày bắt đầu và ngày kết thúc."
      : days < 1
        ? "Ngày bắt đầu phải trước ngày kết thúc."
        : days > MAX_DAYS
          ? `Chỉ xem được tối đa ${MAX_DAYS} ngày một lần.`
          : null;
  // Khoảng dài mà vẫn chọn "theo ngày" thì tự chuyển sang tháng thay vì để backend trả 400.
  const effectiveInterval: ReportInterval = interval === "DAY" && days > MAX_DAYS_DAILY ? "MONTH" : interval;
  const enabled = rangeError === null;

  const revenue = useQuery({
    queryKey: ["reporting", "revenue", range, effectiveInterval],
    queryFn: () => reportingApi.revenue(range, effectiveInterval),
    enabled,
    placeholderData: keepPreviousData,
  });
  const appointments = useQuery({
    queryKey: ["reporting", "appointments", range, effectiveInterval],
    queryFn: () => reportingApi.appointments(range, effectiveInterval),
    enabled,
    placeholderData: keepPreviousData,
  });

  const activePreset = range.to === today ? PRESETS.find((p) => range.from === shiftDays(today, -(p.days - 1))) : undefined;

  function choosePreset(daysBack: number) {
    setRange({ from: shiftDays(today, -(daysBack - 1)), to: today });
    setGrouping(daysBack > MAX_DAYS_DAILY ? "MONTH" : "DAY");
  }

  const totals = revenue.data?.totals;
  const appt = appointments.data;

  return (
    <>
      <PageHeader
        title="Báo cáo"
        description="Doanh thu bán hàng, tiền thu tại phòng khám và kết quả lịch khám theo thời gian."
      />

      <div className="mb-5 flex flex-wrap items-end gap-3">
        <div role="group" aria-label="Khoảng thời gian nhanh" className="flex rounded-[var(--radius-control)] border border-line-strong bg-surface p-0.5">
          {PRESETS.map((p) => (
            <button
              key={p.days}
              type="button"
              aria-pressed={activePreset?.days === p.days}
              onClick={() => choosePreset(p.days)}
              className={cn(
                "h-8 rounded-[4px] px-3 text-sm transition-colors duration-[120ms]",
                activePreset?.days === p.days ? "bg-moss text-white" : "text-ink-soft hover:bg-paper hover:text-ink",
              )}
            >
              {p.label}
            </button>
          ))}
        </div>

        <div>
          <label htmlFor="rp-from" className="mb-1.5 block text-sm font-medium">
            Từ ngày
          </label>
          <input
            id="rp-from"
            type="date"
            value={range.from}
            max={range.to || undefined}
            onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
            className={`${controlClass} h-9`}
          />
        </div>
        <div>
          <label htmlFor="rp-to" className="mb-1.5 block text-sm font-medium">
            Đến ngày
          </label>
          <input
            id="rp-to"
            type="date"
            value={range.to}
            min={range.from || undefined}
            onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
            className={`${controlClass} h-9`}
          />
        </div>
        <div>
          <label htmlFor="rp-interval" className="mb-1.5 block text-sm font-medium">
            Gộp số liệu
          </label>
          <select
            id="rp-interval"
            value={effectiveInterval}
            onChange={(e) => setGrouping(e.target.value as ReportInterval)}
            className={`${controlClass} h-9 pr-8`}
          >
            {INTERVALS.map((i) => (
              <option key={i.value} value={i.value} disabled={i.value === "DAY" && days > MAX_DAYS_DAILY}>
                {i.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {rangeError && <ErrorState message={rangeError} className="mb-5" />}

      {revenue.data && <UnavailableNote sources={revenue.data.unavailable} />}

      <div className="mt-3">
        <MetricRow>
          <Metric label="Tổng doanh thu" value={totals ? formatPrice(totals.total) : null} icon={Wallet} loading={revenue.isLoading} />
          <Metric
            label="Bán hàng"
            hint={totals?.ordersCompleted != null ? `${totals.ordersCompleted} đơn đã giao` : undefined}
            value={totals ? formatPrice(totals.sales) : null}
            icon={Receipt}
            loading={revenue.isLoading}
          />
          <Metric
            label="Khám và đơn thuốc"
            hint={totals?.payments != null ? `${totals.payments} lượt thu tại quầy` : undefined}
            value={totals ? formatPrice(totals.services) : null}
            icon={CalendarCheck}
            loading={revenue.isLoading}
          />
          <Metric
            label="Tỉ lệ huỷ lịch"
            value={appt ? percent(appt.cancellationRate) : null}
            icon={Ban}
            loading={appointments.isLoading}
            tone={appt?.cancellationRate != null && appt.cancellationRate >= 0.2 ? "alert" : "plain"}
          />
        </MetricRow>
      </div>

      <div className="mt-6">
        <TableFrame title="Doanh thu">
          <div className="p-4">
            {revenue.isError ? (
              <ErrorState message={errorText(revenue.error, "Không tải được doanh thu.")} onRetry={() => revenue.refetch()} />
            ) : !revenue.data ? (
              <Skeleton className="h-[300px] w-full" />
            ) : (
              <RevenueChart report={revenue.data} />
            )}
          </div>
        </TableFrame>
      </div>

      <div className="mt-6 grid gap-5 xl:grid-cols-5">
        <div className="xl:col-span-3">
          <TableFrame title="Lịch khám" count={appt?.totals.total}>
            <div className="p-4">
              {appointments.isError ? (
                <ErrorState
                  message={errorText(appointments.error, "Không tải được số liệu lịch khám.")}
                  onRetry={() => appointments.refetch()}
                />
              ) : !appt ? (
                <Skeleton className="h-[260px] w-full" />
              ) : (
                <>
                  <dl className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                    <Stat label="Đã khám" value={appt.totals.completed} />
                    <Stat label="Chờ khám" value={appt.totals.pending + appt.totals.confirmed} />
                    <Stat label="Không đến" value={appt.totals.noShow} hint={percent(appt.noShowRate)} />
                    <Stat label="Đã huỷ" value={appt.totals.cancelled} hint={percent(appt.cancellationRate)} />
                  </dl>
                  <AppointmentChart report={appt} />
                </>
              )}
            </div>
          </TableFrame>
        </div>

        <div className="xl:col-span-2">
          <TableFrame title="Theo bác sĩ" count={appt?.byDoctor.length}>
            {appointments.isError ? null : !appt ? (
              <div className="p-4">
                <Skeleton className="h-40 w-full" />
              </div>
            ) : (
              <DataTable
                caption="Số lịch khám theo bác sĩ"
                rows={appt.byDoctor}
                keyOf={(d) => d.doctorUserId}
                columns={DOCTOR_COLUMNS}
                empty={<EmptyState title="Không có lịch khám nào trong khoảng này" />}
              />
            )}
          </TableFrame>
        </div>
      </div>
    </>
  );
}

const DOCTOR_COLUMNS: Column<DoctorRow>[] = [
  {
    key: "doctor",
    header: "Bác sĩ",
    cell: (d) => (d.doctorName ? `BS. ${d.doctorName}` : <span className="text-bark">Chưa rõ tên</span>),
  },
  { key: "total", header: "Tổng", numeric: true, cell: (d) => d.counts.total },
  { key: "completed", header: "Đã khám", numeric: true, cell: (d) => d.counts.completed },
  { key: "cancel", header: "Huỷ", numeric: true, hideBelow: "sm", cell: (d) => percent(d.cancellationRate) },
];

function Stat({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <div>
      <dt className="text-sm text-bark">{label}</dt>
      <dd className="mt-0.5 text-lg text-ink tnum">
        {value}
        {hint && hint !== "—" && <span className="ml-1.5 text-sm text-bark">{hint}</span>}
      </dd>
    </div>
  );
}

function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.status === 503) {
    return "Dịch vụ lịch khám đang không phản hồi. Thử tải lại sau ít phút.";
  }
  return fallback;
}
