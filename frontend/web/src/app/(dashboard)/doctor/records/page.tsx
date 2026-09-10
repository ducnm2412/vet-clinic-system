"use client";

import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ApiError, medicalRecordApi } from "@/lib/api";
import { PRESCRIPTION_STATUS } from "@/lib/utils/status";
import { formatDateTime } from "@/lib/utils/format";
import type { MedicalRecord } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  StatusTag,
  TableFrame,
  TableSkeleton,
  type Column,
} from "@/components/ui";

/**
 * Bệnh án có đơn thuốc chưa xong. `GET /booking/medical-records/pending` trả những bản
 * ghi mà đơn thuốc chưa thu tiền hoặc chưa phát — đây là việc còn treo của phòng khám,
 * không phải toàn bộ bệnh án đã lập.
 */
export default function DoctorRecordsPage() {
  const router = useRouter();
  const records = useQuery({
    queryKey: ["medical-records", "pending"],
    queryFn: medicalRecordApi.pending,
  });

  const columns: Column<MedicalRecord>[] = [
    {
      key: "diagnosis",
      header: "Chẩn đoán",
      cell: (r) => <span className="font-medium text-ink">{r.diagnosis}</span>,
    },
    {
      key: "items",
      header: "Số thuốc",
      numeric: true,
      cell: (r) => r.prescriptionItems.length,
    },
    {
      key: "status",
      header: "Đơn thuốc",
      cell: (r) => <StatusTag status={PRESCRIPTION_STATUS[r.status]} />,
    },
    {
      key: "created",
      header: "Lập lúc",
      hideBelow: "lg",
      cell: (r) => <span className="text-bark">{formatDateTime(r.createdAt)}</span>,
    },
    {
      key: "action",
      header: "Thao tác",
      cell: (r) => (
        <div className="flex justify-end">
          <Button
            size="sm"
            variant="secondary"
            onClick={() => router.push(`/doctor/appointments/${r.appointmentId}`)}
          >
            Mở hồ sơ
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Bệnh án còn treo"
        description="Những ca đã kê đơn nhưng khách chưa trả tiền hoặc chưa nhận thuốc."
      />

      <TableFrame title="Đơn thuốc chờ xử lý" count={records.data?.length}>
        {records.isLoading ? (
          <TableSkeleton rows={4} cols={4} />
        ) : records.isError ? (
          <div className="p-4">
            <ErrorState
              message={
                records.error instanceof ApiError ? records.error.message : "Không tải được bệnh án."
              }
              onRetry={() => records.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Bệnh án có đơn thuốc chưa xong"
            rows={records.data ?? []}
            keyOf={(r) => r.id}
            columns={columns}
            onRowClick={(r) => router.push(`/doctor/appointments/${r.appointmentId}`)}
            empty={
              <EmptyState
                title="Không còn bệnh án nào treo"
                description="Mọi đơn thuốc đã được thu tiền và phát cho khách."
              />
            }
          />
        )}
      </TableFrame>
    </>
  );
}
