"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatDate, formatPrice } from "@/lib/utils/format";
import type { RevenueReport } from "@/types";
import { CHART, compactNumber, periodLabel } from "./format";

/**
 * Cột chồng: bán hàng (đơn đã giao) và thu tại phòng khám (đơn thuốc đã thanh toán).
 * Nguồn nào không trả lời thì không vẽ lớp đó — không vẽ thành cột 0.
 */
export function RevenueChart({ report, height = 280 }: { report: RevenueReport; height?: number }) {
  const hasSales = !report.unavailable.includes("sales");
  const hasServices = !report.unavailable.includes("services");

  const data = report.points.map((p) => ({
    label: periodLabel(report.interval, p.periodStart),
    periodStart: p.periodStart,
    periodEnd: p.periodEnd,
    sales: p.sales ?? 0,
    services: p.services ?? 0,
  }));

  return (
    <div>
      <Legend hasSales={hasSales} hasServices={hasServices} />
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
              width={52}
              tickLine={false}
              axisLine={false}
              tick={{ fill: CHART.axis, fontSize: 12 }}
              tickFormatter={compactNumber}
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
                    </p>
                    {hasSales && <TooltipRow color={CHART.sales} label="Bán hàng" value={row.sales} />}
                    {hasServices && <TooltipRow color={CHART.services} label="Khám và đơn thuốc" value={row.services} />}
                    {hasSales && hasServices && (
                      <p className="mt-1 border-t border-line pt-1 tnum text-ink">
                        Tổng {formatPrice(row.sales + row.services)}
                      </p>
                    )}
                  </div>
                );
              }}
            />
            {hasServices && <Bar dataKey="services" stackId="revenue" fill={CHART.services} isAnimationActive={false} />}
            {hasSales && (
              <Bar dataKey="sales" stackId="revenue" fill={CHART.sales} radius={[3, 3, 0, 0]} isAnimationActive={false} />
            )}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function Legend({ hasSales, hasServices }: { hasSales: boolean; hasServices: boolean }) {
  return (
    <ul className="flex flex-wrap gap-x-5 gap-y-1 text-sm text-ink-soft">
      {hasSales && <LegendItem color={CHART.sales} label="Bán hàng (đơn đã giao)" />}
      {hasServices && <LegendItem color={CHART.services} label="Khám và đơn thuốc (đã thu)" />}
    </ul>
  );
}

export function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <li className="flex items-center gap-2">
      <span aria-hidden className="size-2.5 rounded-sm" style={{ background: color }} />
      {label}
    </li>
  );
}

function TooltipRow({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <p className="mt-0.5 flex items-center gap-2 text-ink-soft">
      <span aria-hidden className="size-2 rounded-sm" style={{ background: color }} />
      {label}
      <span className="ml-auto pl-4 tnum text-ink">{formatPrice(value)}</span>
    </p>
  );
}
