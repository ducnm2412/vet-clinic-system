"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, bookingApi } from "@/lib/api";
import { APPOINTMENT_STATUS } from "@/lib/utils/status";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import type { Appointment, AppointmentStatus } from "@/types";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  SelectField,
  StatusTag,
  TableFrame,
  TableSkeleton,
  controlClass,
  useToast,
  type Column,
} from "@/components/ui";

const STATUSES: AppointmentStatus[] = ["PENDING", "CONFIRMED", "COMPLETED", "CANCELLED", "NO_SHOW"];

/**
 * Tra cứu và xử lý lịch khám cho nhân viên và quản trị.
 *
 * Bảng không có cột bác sĩ: `AppointmentResponse` không trả `doctorUserId` dù endpoint lại
 * lọc được theo trường đó (VD-16). Thêm cột trống chỉ để "cho đủ" sẽ gây hiểu nhầm.
 */
export function AppointmentsView() {
  const qc = useQueryClient();
  const toast = useToast();

  const [date, setDate] = useState(todayISO());
  const [status, setStatus] = useState<AppointmentStatus | "">("");

  const appointments = useQuery({
    queryKey: ["appointments", { date, status }],
    queryFn: () =>
      bookingApi.search({ date: date || undefined, status: status || undefined }),
  });

  const updateStatus = useMutation({
    mutationFn: ({ id, next }: { id: string; next: AppointmentStatus }) =>
      bookingApi.updateStatus(id, next),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      toast.success("Đã đổi trạng thái lịch khám");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không đổi được trạng thái."),
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
      cell: (a) => <span className="text-ink-soft">{a.reason || "Không ghi"}</span>,
    },
    {
      key: "status",
      header: "Trạng thái",
      cell: (a) => <StatusTag status={APPOINTMENT_STATUS[a.status]} />,
    },
    {
      key: "action",
      header: "Đổi trạng thái",
      cell: (a) =>
        a.status === "PENDING" ? (
          <div className="flex justify-end gap-1">
            <Button
              size="sm"
              loading={updateStatus.isPending && updateStatus.variables?.id === a.id}
              onClick={() => updateStatus.mutate({ id: a.id, next: "CONFIRMED" })}
            >
              Xác nhận
            </Button>
          </div>
        ) : a.status === "CONFIRMED" ? (
          <div className="flex justify-end gap-1">
            <Button
              size="sm"
              variant="secondary"
              loading={updateStatus.isPending && updateStatus.variables?.id === a.id}
              onClick={() => updateStatus.mutate({ id: a.id, next: "COMPLETED" })}
            >
              Đã khám
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => updateStatus.mutate({ id: a.id, next: "NO_SHOW" })}
            >
              Không đến
            </Button>
          </div>
        ) : null,
    },
  ];

  return (
    <>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="ap-date" className="mb-1.5 block text-sm font-medium">
            Ngày
          </label>
          <input
            id="ap-date"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className={`${controlClass} h-9`}
          />
        </div>
        <div className="min-w-44">
          <SelectField
            label="Trạng thái"
            value={status}
            onChange={(e) => setStatus(e.target.value as AppointmentStatus | "")}
          >
            <option value="">Tất cả</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {APPOINTMENT_STATUS[s].label}
              </option>
            ))}
          </SelectField>
        </div>
        <Button variant="secondary" onClick={() => setDate(todayISO())}>
          Hôm nay
        </Button>
        <Button variant="ghost" onClick={() => setDate("")}>
          Bỏ lọc ngày
        </Button>
      </div>

      <TableFrame title={date ? `Lịch ngày ${formatDate(date)}` : "Tất cả lịch khám"} count={rows.length}>
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
            caption="Lịch khám"
            rows={rows}
            keyOf={(a) => a.id}
            columns={columns}
            empty={<EmptyState title="Không có lịch khám nào khớp điều kiện" />}
          />
        )}
      </TableFrame>
    </>
  );
}
