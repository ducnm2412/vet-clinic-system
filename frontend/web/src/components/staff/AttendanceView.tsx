"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { LogIn, LogOut } from "lucide-react";
import { ApiError, attendanceApi } from "@/lib/api";
import { formatDate } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  Skeleton,
  TableFrame,
  TableSkeleton,
  useToast,
  type Column,
} from "@/components/ui";
import type { AttendanceRecord } from "@/types";

/** "08:15" theo giờ Việt Nam; null thì "—". */
function clock(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "Asia/Ho_Chi_Minh",
  });
}

/**
 * 185 phút → "3 giờ 5 phút".
 *
 * Ngày công đã xong mà chưa tới một phút vẫn phải nói ra — hiện "—" ở đó trông như chưa chấm
 * công, trong khi thực tế là vào ra quá nhanh.
 */
export function duration(minutes: number, finished = true): string {
  if (!minutes) return finished ? "dưới 1 phút" : "—";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!hours) return `${rest} phút`;
  return rest ? `${hours} giờ ${rest} phút` : `${hours} giờ`;
}

function shiftLabel(record: AttendanceRecord): string {
  if (!record.shiftStart || !record.shiftEnd) return "Ngoài ca";
  return `${record.shiftStart.slice(0, 5)}–${record.shiftEnd.slice(0, 5)}`;
}

const isoDaysAgo = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

/**
 * CN-38: chấm công của chính mình. Dùng chung cho bác sĩ và nhân viên quầy — cùng một việc,
 * không có lý do gì làm hai trang khác nhau.
 */
export function AttendanceView() {
  const qc = useQueryClient();
  const toast = useToast();

  const today = useQuery({ queryKey: ["attendance", "today"], queryFn: attendanceApi.today });
  const history = useQuery({
    queryKey: ["attendance", "mine", 14],
    queryFn: () => attendanceApi.mine({ from: isoDaysAgo(13), to: isoDaysAgo(0) }),
  });

  const record = today.data ?? null;
  const working = record !== null && record.checkOutAt === null;
  const finished = record !== null && record.checkOutAt !== null;

  const punch = useMutation({
    mutationFn: () => (working ? attendanceApi.checkOut() : attendanceApi.checkIn()),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ["attendance"] });
      toast.success(
        saved.checkOutAt
          ? `Đã ra ca lúc ${clock(saved.checkOutAt)} — hôm nay làm ${duration(saved.workedMinutes)}`
          : `Đã vào ca lúc ${clock(saved.checkInAt)}`,
      );
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không chấm công được."),
  });

  const columns: Column<AttendanceRecord>[] = [
    { key: "date", header: "Ngày", cell: (r) => <span className="tnum">{formatDate(r.date)}</span> },
    { key: "shift", header: "Ca", hideBelow: "sm", cell: (r) => <span className="text-bark">{shiftLabel(r)}</span> },
    { key: "in", header: "Vào", numeric: true, cell: (r) => <span className="tnum">{clock(r.checkInAt)}</span> },
    { key: "out", header: "Ra", numeric: true, cell: (r) => <span className="tnum">{clock(r.checkOutAt)}</span> },
    {
      key: "worked",
      header: "Số giờ",
      numeric: true,
      cell: (r) =>
        r.checkOutAt ? (
          <span className="tnum">{duration(r.workedMinutes)}</span>
        ) : (
          <span className="text-amber">Chưa ra ca</span>
        ),
    },
  ];

  return (
    <>
      <PageHeader title="Chấm công" description="Vào ca khi bắt đầu làm và ra ca khi nghỉ." />

      <section className="rounded-[var(--radius-control)] border border-line bg-surface p-5">
        {today.isLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : today.isError ? (
          <ErrorState message="Không tải được trạng thái hôm nay." onRetry={() => today.refetch()} />
        ) : (
          <div className="flex flex-wrap items-center gap-4">
            <div className="min-w-0 flex-1">
              <p className="text-sm text-bark">Hôm nay</p>
              <p className="mt-0.5 font-[family-name:var(--font-display)] text-[25px] leading-tight text-ink">
                {working
                  ? `Đang trong ca từ ${clock(record.checkInAt)}`
                  : finished
                    ? `Đã làm ${duration(record.workedMinutes)}`
                    : "Chưa vào ca"}
              </p>
              {record?.shiftStart && (
                <p className="mt-1 text-sm text-bark">Ca được xếp: {shiftLabel(record)}</p>
              )}
            </div>

            {finished ? (
              <p className="text-sm text-bark">
                Vào {clock(record.checkInAt)} · Ra {clock(record.checkOutAt)}
              </p>
            ) : (
              <Button size="lg" loading={punch.isPending} onClick={() => punch.mutate()}>
                {working ? <LogOut aria-hidden className="size-4" /> : <LogIn aria-hidden className="size-4" />}
                {working ? "Ra ca" : "Vào ca"}
              </Button>
            )}
          </div>
        )}
      </section>

      <div className="mt-6">
        <TableFrame title="14 ngày gần nhất" count={history.data?.length}>
          {history.isLoading ? (
            <TableSkeleton rows={5} cols={5} />
          ) : history.isError ? (
            <div className="p-4">
              <ErrorState message="Không tải được lịch sử chấm công." onRetry={() => history.refetch()} />
            </div>
          ) : (
            <DataTable
              caption="Chấm công của tôi"
              rows={history.data ?? []}
              keyOf={(r) => r.id}
              columns={columns}
              empty={<EmptyState title="Chưa có ngày công nào" description="Bấm Vào ca để bắt đầu ngày làm việc." />}
            />
          )}
        </TableFrame>
      </div>
    </>
  );
}
