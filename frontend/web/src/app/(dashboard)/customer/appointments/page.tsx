"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, bookingApi } from "@/lib/api";
import { APPOINTMENT_STATUS } from "@/lib/utils/status";
import { formatDate, formatTime } from "@/lib/utils/format";
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
  useToast,
  type Column,
} from "@/components/ui";

export default function CustomerAppointmentsPage() {
  const qc = useQueryClient();
  const toast = useToast();

  const appointments = useQuery({ queryKey: ["appointments", "mine"], queryFn: bookingApi.mine });

  const cancel = useMutation({
    mutationFn: (id: string) => bookingApi.cancel(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      toast.success("Đã huỷ lịch khám");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không huỷ được lịch."),
  });

  const rows = [...(appointments.data ?? [])].sort((a, b) =>
    `${b.date}${b.startTime}`.localeCompare(`${a.date}${a.startTime}`),
  );

  const columns: Column<Appointment>[] = [
    {
      key: "when",
      header: "Thời gian",
      cell: (a) => (
        <span className="whitespace-nowrap">
          {formatDate(a.date)} lúc <span className="tnum">{formatTime(a.startTime)}</span>
        </span>
      ),
    },
    {
      key: "reason",
      header: "Lý do khám",
      hideBelow: "lg",
      cell: (a) => <span className="text-ink-soft">{a.reason || "Không ghi"}</span>,
    },
    {
      key: "status",
      header: "Trạng thái",
      cell: (a) => <StatusTag status={APPOINTMENT_STATUS[a.status]} />,
    },
    {
      key: "action",
      header: "Thao tác",
      cell: (a) =>
        a.status === "PENDING" || a.status === "CONFIRMED" ? (
          <div className="flex justify-end">
            <Button
              size="sm"
              variant="danger"
              loading={cancel.isPending && cancel.variables === a.id}
              onClick={() => {
                if (confirm(`Huỷ lịch khám ngày ${formatDate(a.date)}?`)) cancel.mutate(a.id);
              }}
            >
              Huỷ lịch
            </Button>
          </div>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader
        title="Lịch khám"
        description="Lịch đã đặt cho các bé nhà bạn."
        actions={
          <Link href="/customer/appointments/create">
            <Button>Đặt lịch khám</Button>
          </Link>
        }
      />

      <TableFrame title="Lịch của tôi" count={rows.length}>
        {appointments.isLoading ? (
          <TableSkeleton rows={4} cols={4} />
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
            caption="Lịch khám của khách"
            rows={rows}
            keyOf={(a) => a.id}
            columns={columns}
            empty={
              <EmptyState
                title="Chưa có lịch khám nào"
                description="Đặt lịch để bác sĩ sắp xếp khung giờ cho bé."
                action={
                  <Link href="/customer/appointments/create">
                    <Button size="sm">Đặt lịch khám</Button>
                  </Link>
                }
              />
            }
          />
        )}
      </TableFrame>
    </>
  );
}
