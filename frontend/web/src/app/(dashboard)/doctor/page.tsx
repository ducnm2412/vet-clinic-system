"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarCheck, CircleDot, ClipboardPlus, Clock } from "lucide-react";
import { bookingApi, medicalRecordApi } from "@/lib/api";
import { APPOINTMENT_STATUS, PRESCRIPTION_STATUS } from "@/lib/utils/status";
import { formatTime, todayISO } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Metric, MetricRow } from "@/components/dashboard/Metric";
import {
  DataTable,
  EmptyState,
  ErrorState,
  StatusTag,
  TableFrame,
  TableSkeleton,
  type Column,
} from "@/components/ui";
import type { Appointment, MedicalRecord } from "@/types";

export default function DoctorDashboard() {
  const today = todayISO();

  const todayList = useQuery({
    queryKey: ["appointments", "doctor", { date: today }],
    queryFn: () => bookingApi.forMeAsDoctor(today),
  });

  const pendingRecords = useQuery({
    queryKey: ["medical-records", "pending"],
    queryFn: medicalRecordApi.pending,
  });

  const list = todayList.data ?? [];
  const waiting = list.filter((a) => a.status === "PENDING" || a.status === "CONFIRMED").length;
  const done = list.filter((a) => a.status === "COMPLETED").length;

  return (
    <>
      <PageHeader title="Hôm nay" description="Ca khám của bạn và bệnh án còn dở." />

      <MetricRow>
        <Metric label="Ca hôm nay" value={list.length} icon={CalendarCheck} loading={todayList.isLoading} />
        <Metric
          label="Đang chờ khám"
          value={waiting}
          icon={Clock}
          loading={todayList.isLoading}
          tone={waiting ? "alert" : "plain"}
        />
        <Metric label="Đã khám xong" value={done} icon={CircleDot} loading={todayList.isLoading} />
        <Metric
          label="Đơn thuốc chờ thu tiền"
          value={pendingRecords.data?.length ?? null}
          icon={ClipboardPlus}
          loading={pendingRecords.isLoading}
        />
      </MetricRow>

      <div className="mt-6 grid gap-5 xl:grid-cols-2">
        <TableFrame title="Ca khám hôm nay" count={list.length}>
          {todayList.isLoading ? (
            <TableSkeleton rows={5} cols={3} />
          ) : todayList.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được lịch khám." onRetry={() => todayList.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Ca khám của bác sĩ trong ngày"
              rows={list}
              keyOf={(a) => a.id}
              columns={TODAY_COLUMNS}
              empty={<EmptyState title="Hôm nay bạn chưa có ca nào" />}
            />
          )}
        </TableFrame>

        <TableFrame title="Đơn thuốc chờ xử lý" count={pendingRecords.data?.length}>
          {pendingRecords.isLoading ? (
            <TableSkeleton rows={4} cols={2} />
          ) : pendingRecords.isError ? (
            <div className="p-4">
              <ErrorState
                message="Không tải được danh sách đơn thuốc."
                onRetry={() => pendingRecords.refetch()}
              />
            </div>
          ) : (
            <DataTable
              caption="Đơn thuốc đang chờ"
              rows={pendingRecords.data ?? []}
              keyOf={(r) => r.id}
              columns={RECORD_COLUMNS}
              empty={<EmptyState title="Không còn đơn thuốc nào chờ xử lý" />}
            />
          )}
        </TableFrame>
      </div>
    </>
  );
}

const TODAY_COLUMNS: Column<Appointment>[] = [
  { key: "time", header: "Giờ", cell: (a) => formatTime(a.startTime) },
  {
    key: "status",
    header: "Trạng thái",
    cell: (a) => <StatusTag status={APPOINTMENT_STATUS[a.status]} />,
  },
  {
    key: "reason",
    header: "Lý do khám",
    hideBelow: "lg",
    cell: (a) => <span className="text-ink-soft">{a.reason || "Không ghi"}</span>,
  },
];

const RECORD_COLUMNS: Column<MedicalRecord>[] = [
  { key: "diagnosis", header: "Chẩn đoán", cell: (r) => r.diagnosis },
  {
    key: "items",
    header: "Số thuốc",
    numeric: true,
    cell: (r) => r.prescriptionItems.length,
  },
  {
    key: "status",
    header: "Trạng thái",
    cell: (r) => <StatusTag status={PRESCRIPTION_STATUS[r.status]} />,
  },
];
