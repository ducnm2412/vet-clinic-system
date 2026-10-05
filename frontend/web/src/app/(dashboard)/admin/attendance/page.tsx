"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { CalendarCheck, Clock, TriangleAlert } from "lucide-react";
import { ApiError, attendanceApi, authApi } from "@/lib/api";
import { formatDate } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Metric, MetricRow } from "@/components/dashboard/Metric";
import { duration } from "@/components/staff/AttendanceView";
import {
  DataTable,
  EmptyState,
  ErrorState,
  TableFrame,
  TableSkeleton,
  controlClass,
  type Column,
} from "@/components/ui";
import type { AttendanceRecord, TimesheetRow } from "@/types";

const isoDaysAgo = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

function clock(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "Asia/Ho_Chi_Minh",
  });
}

/**
 * CN-40, CN-48: bảng công của cả phòng khám.
 *
 * staff-service chỉ biết userId; họ tên lấy từ auth-service rồi ghép lại — giống cách trang
 * Nhân viên làm.
 */
export default function AdminAttendancePage() {
  const [range, setRange] = useState({ from: isoDaysAgo(29), to: isoDaysAgo(0) });

  const timesheet = useQuery({
    queryKey: ["attendance", "timesheet", range],
    queryFn: () => attendanceApi.timesheet(range),
    placeholderData: keepPreviousData,
  });
  const records = useQuery({
    queryKey: ["attendance", "all", range],
    queryFn: () => attendanceApi.all(range),
    placeholderData: keepPreviousData,
  });
  const people = useQuery({
    queryKey: ["admin-users", { size: 100 }],
    queryFn: () => authApi.listUsers({ size: 100 }),
  });

  const nameOf = (userId: string) => {
    const user = people.data?.content.find((u) => u.id === userId);
    return user ? `${user.firstName} ${user.lastName}`.trim() : "Chưa rõ tên";
  };

  const rows = timesheet.data?.rows ?? [];
  const totalMinutes = rows.reduce((sum, r) => sum + r.totalMinutes, 0);
  const missing = rows.reduce((sum, r) => sum + r.daysMissingCheckOut, 0);

  const timesheetColumns: Column<TimesheetRow>[] = [
    { key: "name", header: "Người", cell: (r) => <span className="font-medium text-ink">{nameOf(r.userId)}</span> },
    { key: "days", header: "Ngày công", numeric: true, cell: (r) => <span className="tnum">{r.daysWorked}</span> },
    { key: "hours", header: "Tổng giờ", numeric: true, cell: (r) => <span className="tnum">{r.totalMinutes ? duration(r.totalMinutes) : "—"}</span> },
    {
      key: "shifts",
      header: "Ca được xếp",
      numeric: true,
      hideBelow: "sm",
      cell: (r) => <span className="tnum text-bark">{r.shiftsAssigned}</span>,
    },
    {
      key: "missing",
      header: "Quên ra ca",
      numeric: true,
      cell: (r) =>
        r.daysMissingCheckOut ? (
          <span className="tnum text-amber">{r.daysMissingCheckOut}</span>
        ) : (
          <span className="text-bark">—</span>
        ),
    },
  ];

  const recordColumns: Column<AttendanceRecord>[] = [
    { key: "date", header: "Ngày", cell: (r) => <span className="tnum">{formatDate(r.date)}</span> },
    { key: "name", header: "Người", cell: (r) => nameOf(r.userId) },
    { key: "in", header: "Vào", numeric: true, cell: (r) => <span className="tnum">{clock(r.checkInAt)}</span> },
    { key: "out", header: "Ra", numeric: true, cell: (r) => <span className="tnum">{clock(r.checkOutAt)}</span> },
    {
      key: "worked",
      header: "Số giờ",
      numeric: true,
      hideBelow: "sm",
      cell: (r) => (r.checkOutAt ? <span className="tnum">{duration(r.workedMinutes)}</span> : <span className="text-amber">Chưa ra ca</span>),
    },
  ];

  return (
    <>
      <PageHeader title="Chấm công" description="Giờ công thực tế của bác sĩ và nhân viên trong kỳ." />

      <div className="mb-5 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="at-from" className="mb-1.5 block text-sm font-medium">
            Từ ngày
          </label>
          <input
            id="at-from"
            type="date"
            value={range.from}
            max={range.to}
            onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
            className={`${controlClass} h-9`}
          />
        </div>
        <div>
          <label htmlFor="at-to" className="mb-1.5 block text-sm font-medium">
            Đến ngày
          </label>
          <input
            id="at-to"
            type="date"
            value={range.to}
            min={range.from}
            onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
            className={`${controlClass} h-9`}
          />
        </div>
      </div>

      <MetricRow>
        <Metric label="Tổng giờ công" value={timesheet.data ? (totalMinutes ? duration(totalMinutes) : "—") : null} icon={Clock} loading={timesheet.isLoading} />
        <Metric label="Người có chấm công" value={timesheet.data ? rows.length : null} icon={CalendarCheck} loading={timesheet.isLoading} />
        <Metric
          label="Ngày quên ra ca"
          value={timesheet.data ? missing : null}
          icon={TriangleAlert}
          loading={timesheet.isLoading}
          tone={missing ? "alert" : "plain"}
        />
      </MetricRow>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        <TableFrame title="Bảng công" count={rows.length}>
          {timesheet.isLoading ? (
            <TableSkeleton rows={4} cols={4} />
          ) : timesheet.isError ? (
            <div className="p-4">
              <ErrorState
                message={timesheet.error instanceof ApiError ? timesheet.error.message : "Không tải được bảng công."}
                onRetry={() => timesheet.refetch()}
              />
            </div>
          ) : (
            <DataTable
              caption="Giờ công theo người"
              rows={rows}
              keyOf={(r) => r.userId}
              columns={timesheetColumns}
              empty={<EmptyState title="Chưa ai chấm công trong khoảng này" />}
            />
          )}
        </TableFrame>

        <TableFrame title="Từng ngày công" count={records.data?.length}>
          {records.isLoading ? (
            <TableSkeleton rows={4} cols={4} />
          ) : records.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được danh sách chấm công." onRetry={() => records.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Chấm công từng ngày"
              rows={records.data ?? []}
              keyOf={(r) => r.id}
              columns={recordColumns}
              empty={<EmptyState title="Chưa có ngày công nào" />}
            />
          )}
        </TableFrame>
      </div>
    </>
  );
}
