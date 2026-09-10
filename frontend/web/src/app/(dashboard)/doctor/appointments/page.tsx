"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ApiError, bookingApi } from "@/lib/api";
import { APPOINTMENT_STATUS } from "@/lib/utils/status";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import type { Appointment } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  StatusTag,
  TableFrame,
  TableSkeleton,
  controlClass,
  type Column,
} from "@/components/ui";

/**
 * Ca khám của chính bác sĩ đang đăng nhập. Backend lọc theo người dùng trong token nên
 * không cần và cũng không thể xem lịch của bác sĩ khác từ đây.
 */
export default function DoctorAppointmentsPage() {
  const router = useRouter();
  const [date, setDate] = useState(todayISO());

  const appointments = useQuery({
    queryKey: ["appointments", "doctor", { date }],
    queryFn: () => bookingApi.forMeAsDoctor(date || undefined),
  });

  const rows = [...(appointments.data ?? [])].sort((a, b) =>
    `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`),
  );

  const columns: Column<Appointment>[] = [
    {
      key: "time",
      header: "Giờ",
      cell: (a) => (
        <span className="tnum font-medium text-ink">
          {formatTime(a.startTime)}–{formatTime(a.endTime)}
        </span>
      ),
    },
    { key: "date", header: "Ngày", hideBelow: "lg", cell: (a) => formatDate(a.date) },
    {
      key: "reason",
      header: "Lý do khám",
      cell: (a) => <span className="text-ink-soft">{a.reason || "Chủ nuôi không ghi"}</span>,
    },
    {
      key: "status",
      header: "Trạng thái",
      cell: (a) => <StatusTag status={APPOINTMENT_STATUS[a.status]} />,
    },
    {
      key: "action",
      header: "Thao tác",
      cell: (a) => (
        <div className="flex justify-end">
          <Button size="sm" variant="secondary" onClick={() => router.push(`/doctor/appointments/${a.id}`)}>
            Mở hồ sơ
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Ca khám của tôi"
        description="Chọn ngày để xem lịch. Mở hồ sơ để ghi bệnh án và kê đơn."
      />

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="date" className="mb-1.5 block text-sm font-medium">
            Ngày khám
          </label>
          <input
            id="date"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className={`${controlClass} h-9`}
          />
        </div>
        <Button variant="secondary" onClick={() => setDate(todayISO())}>
          Hôm nay
        </Button>
      </div>

      <TableFrame title={date ? `Ca khám ngày ${formatDate(date)}` : "Tất cả ca khám"} count={rows.length}>
        {appointments.isLoading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : appointments.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                appointments.error instanceof ApiError
                  ? appointments.error.message
                  : "Không tải được lịch khám."
              }
              onRetry={() => appointments.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Ca khám của bác sĩ"
            rows={rows}
            keyOf={(a) => a.id}
            columns={columns}
            onRowClick={(a) => router.push(`/doctor/appointments/${a.id}`)}
            empty={<EmptyState title="Ngày này bạn không có ca nào" />}
          />
        )}
      </TableFrame>
    </>
  );
}
