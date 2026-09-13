"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatDate } from "@/lib/utils/format";
import type { AppointmentReport } from "@/types";
import { CHART, periodLabel } from "./format";
import { LegendItem } from "./RevenueChart";

const SERIES = [
  { key: "completed", label: "Đã khám", color: CHART.completed },
  { key: "upcoming", label: "Chờ khám", color: CHART.upcoming },
  { key: "noShow", label: "Không đến", color: CHART.noShow },
  { key: "cancelled", label: "Đã huỷ", color: CHART.cancelled },
] as const;

/** Số lịch mỗi kỳ, chồng theo kết quả. "Chờ khám" gộp PENDING và CONFIRMED. */
export function AppointmentChart({ report, height = 240 }: { report: AppointmentReport; height?: number }) {
  const data = report.points.map((p) => ({
    label: periodLabel(report.interval, p.periodStart),
    periodStart: p.periodStart,
    periodEnd: p.periodEnd,
    completed: p.counts.completed,
    upcoming: p.counts.pending + p.counts.confirmed,
    noShow: p.counts.noShow,
    cancelled: p.counts.cancelled,
    total: p.counts.total,
  }));

  return (
    <div>
      <ul className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-soft">
        {SERIES.map((s) => (
          <LegendItem key={s.key} color={s.color} label={s.label} />
        ))}
      </ul>
      <div style={{ height }} className="mt-3">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }} barCategoryGap="22%">
            <CartesianGrid vertical={false} stroke={CHART.grid} />
            <XAxis
              dataKey="label"
              tickLine={false}
              axisLine={{ stroke: CHART.grid }}
              tick={{ fill: CHART.axis, fontSize: 12 }}
              interval="preserveStartEnd"
              minTickGap={12}
            />
            <YAxis
              width={32}
              allowDecimals={false}
              tickLine={false}
              axisLine={false}
              tick={{ fill: CHART.axis, fontSize: 12 }}
            />
            <Tooltip
              cursor={{ fill: "rgba(45, 71, 57, 0.06)" }}
              content={({ active, payload }) => {
                if (!active || !payload?.length) return null;
                const row = payload[0].payload as (typeof data)[number];
                return (
                  <div className="rounded-[var(--radius-control)] border border-line bg-surface px-3 py-2 text-sm shadow-sm">
                    <p className="font-medium text-ink">
                      {row.periodStart === row.periodEnd
                        ? formatDate(row.periodStart)
                        : `${formatDate(row.periodStart)} – ${formatDate(row.periodEnd)}`}
                      <span className="ml-2 font-normal text-bark tnum">{row.total} lịch</span>
                    </p>
                    {SERIES.map((s) => (
                      <p key={s.key} className="mt-0.5 flex items-center gap-2 text-ink-soft">
                        <span aria-hidden className="size-2 rounded-sm" style={{ background: s.color }} />
                        {s.label}
                        <span className="ml-auto pl-4 tnum text-ink">{row[s.key]}</span>
                      </p>
                    ))}
                  </div>
                );
              }}
            />
            {SERIES.map((s, i) => (
              <Bar
                key={s.key}
                dataKey={s.key}
                stackId="appointments"
                fill={s.color}
                radius={i === SERIES.length - 1 ? [3, 3, 0, 0] : undefined}
                isAnimationActive={false}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
