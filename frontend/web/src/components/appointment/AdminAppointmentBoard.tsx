"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, bookingApi } from "@/lib/api";
import { cn } from "@/lib/utils/cn";
import { APPOINTMENT_STATUS } from "@/lib/utils/status";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import { useDoctorDirectory } from "@/lib/useDoctors";
import type { Appointment, AppointmentStatus, DoctorAvailability } from "@/types";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  Skeleton,
  StatusTag,
  TableFrame,
  TableSkeleton,
  Tag,
  controlClass,
  useToast,
  type Column,
} from "@/components/ui";
import { AssignDoctorDialog, type Mode } from "./AssignDoctorDialog";

type Tab = "waiting" | "day";

const isAwaiting = (s: AppointmentStatus) => s === "AWAITING_ASSIGNMENT" || s === "AWAITING_CUSTOMER";

/** Giờ khách chọn ban đầu. Lịch chờ khách đồng ý mang giờ đề xuất ở date/startTime. */
function askedWhen(a: Appointment): { date: string; time: string } {
  return isAwaiting(a.status)
    ? { date: a.preferredDate ?? a.date, time: a.preferredStartTime ?? a.startTime }
    : { date: a.date, time: a.startTime };
}

function doctorName(fullName: string | null | undefined): string {
  return fullName?.trim() ? `BS. ${fullName.trim()}` : "Bác sĩ phòng khám";
}

/**
 * Màn điều phối lịch khám của admin: hàng trên là các bác sĩ có thể phân công, hàng dưới là lịch
 * khách đã đặt. Bấm một lịch đang chờ xếp thì các ô bác sĩ cho biết ai rảnh đúng giờ đó; bấm ô bác sĩ
 * rảnh để xếp. Không ai rảnh thì đề xuất giờ khác (mở hộp thoại đề xuất).
 */
export function AdminAppointmentBoard() {
  const qc = useQueryClient();
  const toast = useToast();
  const directory = useDoctorDirectory();

  const [tab, setTab] = useState<Tab>("waiting");
  const [date, setDate] = useState(todayISO());
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<{ appointment: Appointment; mode: Mode } | null>(null);
  const [confirm, setConfirm] = useState<DoctorAvailability | null>(null);
  const [rejecting, setRejecting] = useState<Appointment | null>(null);

  // Hàng đợi việc: mọi yêu cầu chờ xếp, bất kể ngày. Yêu cầu mới đến liên tục nên tự làm mới nhẹ.
  const awaiting = useQuery({
    queryKey: ["appointments", "awaiting"],
    queryFn: bookingApi.awaiting,
    refetchInterval: 30_000,
  });

  // Lịch trong ngày: vừa là tab thứ hai, vừa để đếm số lịch của từng bác sĩ ở các ô phía trên.
  const day = useQuery({
    queryKey: ["appointments", { date, status: "", doctorUserId: "" }],
    queryFn: () => bookingApi.search({ date }),
    enabled: date !== "",
  });

  const waitingRows = awaiting.data ?? [];
  const dayRows = [...(day.data ?? [])].sort((a, b) =>
    `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`),
  );
  const rows = tab === "waiting" ? waitingRows : dayRows;
  const list = tab === "waiting" ? awaiting : day;

  // Chỉ lịch chờ xếp mới chọn được để xếp bác sĩ.
  const selected =
    [...waitingRows, ...dayRows].find((a) => a.id === selectedId && a.status === "AWAITING_ASSIGNMENT") ?? null;
  const asked = selected ? askedWhen(selected) : null;
  const askedPast = asked !== null && asked.date < todayISO();

  const availability = useQuery({
    queryKey: ["available-doctors", selected?.id, "assign", "", ""],
    queryFn: () => bookingApi.availableDoctors(selected!.id),
    enabled: selected !== null && !askedPast,
  });

  const assign = useMutation({
    mutationFn: ({ id, doctorUserId }: { id: string; doctorUserId: string }) =>
      bookingApi.assign(id, doctorUserId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      qc.invalidateQueries({ queryKey: ["available-doctors"] });
      qc.invalidateQueries({ queryKey: ["clinic-hours"] });
      toast.success("Đã xếp bác sĩ, khách sẽ nhận email xác nhận");
      // Số lịch ở các ô bác sĩ tính theo ngày đang xem: chuyển sang ngày vừa xếp để thấy ngay kết quả.
      if (asked) setDate(asked.date);
      setConfirm(null);
      setSelectedId(null);
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : "Không xếp được lịch này.");
      // Bác sĩ có thể vừa bị người khác xếp mất: tải lại để thấy tình trạng thật.
      availability.refetch();
      setConfirm(null);
    },
  });

  const updateStatus = useMutation({
    mutationFn: ({ id, next }: { id: string; next: AppointmentStatus }) => bookingApi.updateStatus(id, next),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      toast.success("Đã đổi trạng thái lịch khám");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không đổi được trạng thái."),
  });

  const reject = useMutation({
    mutationFn: (id: string) => bookingApi.cancel(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      qc.invalidateQueries({ queryKey: ["available-doctors"] });
      toast.success("Đã huỷ lịch");
      if (rejecting?.id === selectedId) setSelectedId(null);
      setRejecting(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không huỷ được lịch."),
  });

  // Số lịch còn hiệu lực của từng bác sĩ trong ngày đang xem (khi chưa chọn lịch nào).
  const loadByDoctor = new Map<string, number>();
  for (const a of dayRows) {
    if (a.doctorUserId && a.status !== "CANCELLED") {
      loadByDoctor.set(a.doctorUserId, (loadByDoctor.get(a.doctorUserId) ?? 0) + 1);
    }
  }

  const today = todayISO();

  const columns: Column<Appointment>[] = [
    {
      key: "time",
      header: "Giờ",
      cell: (a) => {
        const w = askedWhen(a);
        const pickable = a.status === "AWAITING_ASSIGNMENT";
        return (
          <span className="inline-flex items-center gap-2.5 whitespace-nowrap">
            <span
              aria-hidden
              className={cn(
                "size-3 shrink-0 rounded-full border",
                !pickable
                  ? "border-transparent"
                  : a.id === selected?.id
                    ? "border-moss bg-moss"
                    : "border-line-strong bg-surface",
              )}
            />
            <span>
              <span className="tnum font-medium text-ink">{formatTime(w.time)}</span>
              <span className="text-ink-soft"> · {formatDate(w.date)}</span>
              {a.status === "AWAITING_ASSIGNMENT" && w.date < today && (
                <span className="ml-2 text-xs text-danger">Đã quá giờ</span>
              )}
            </span>
          </span>
        );
      },
    },
    {
      key: "doctor",
      header: "Bác sĩ",
      cell: (a) =>
        a.status === "AWAITING_ASSIGNMENT" ? (
          <span className="text-bark">Chưa xếp</span>
        ) : a.status === "AWAITING_CUSTOMER" ? (
          <span className="text-ink">
            {directory.label(a.doctorUserId)}
            <span className="block text-xs text-bark">
              Đề xuất {formatTime(a.startTime)} · {formatDate(a.date)}
            </span>
          </span>
        ) : (
          <span className="text-ink">{directory.label(a.doctorUserId)}</span>
        ),
    },
    {
      key: "reason",
      header: "Dịch vụ / lý do",
      hideBelow: "md",
      cell: (a) => (
        <span className="text-ink-soft">
          {a.serviceName && <span className="block font-medium text-ink">{a.serviceName}</span>}
          {a.reason?.trim() || (a.serviceName ? "" : "Không ghi")}
        </span>
      ),
    },
    {
      key: "status",
      header: "Trạng thái",
      cell: (a) => <StatusTag status={APPOINTMENT_STATUS[a.status]} />,
    },
    {
      key: "action",
      header: "Xử lý",
      cell: (a) => {
        const busy = updateStatus.isPending && updateStatus.variables?.id === a.id;
        // Bấm nút trong hàng không được tính là chọn hàng.
        return (
          <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
            {a.status === "AWAITING_ASSIGNMENT" && (
              <>
                <Button size="sm" variant="secondary" onClick={() => setDialog({ appointment: a, mode: "propose" })}>
                  Đề xuất giờ khác
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setRejecting(a)}>
                  Huỷ
                </Button>
              </>
            )}
            {a.status === "AWAITING_CUSTOMER" && (
              <>
                <Button size="sm" variant="secondary" onClick={() => setDialog({ appointment: a, mode: "propose" })}>
                  Đổi đề xuất
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setRejecting(a)}>
                  Huỷ
                </Button>
              </>
            )}
            {a.status === "PENDING" && (
              <Button size="sm" loading={busy} onClick={() => updateStatus.mutate({ id: a.id, next: "CONFIRMED" })}>
                Xác nhận
              </Button>
            )}
            {a.status === "CONFIRMED" && (
              <>
                <Button
                  size="sm"
                  variant="secondary"
                  loading={busy}
                  onClick={() => updateStatus.mutate({ id: a.id, next: "COMPLETED" })}
                >
                  Đã khám
                </Button>
                <Button size="sm" variant="ghost" onClick={() => updateStatus.mutate({ id: a.id, next: "NO_SHOW" })}>
                  Không đến
                </Button>
              </>
            )}
          </div>
        );
      },
    },
  ];

  // --- Hàng bác sĩ ---------------------------------------------------------------------------
  const assignable = selected !== null && !askedPast;
  const doctorBoxes: DoctorBox[] = assignable
    ? (availability.data ?? []).map((d) => ({
        id: d.doctorUserId,
        name: doctorName(d.fullName),
        specialty: d.specialty,
        state: d.free ? "free" : "busy",
        onShift: d.onShift,
        load: d.dayLoad,
        source: d,
      }))
    : (directory.data ?? []).map((d) => ({
        id: d.userId,
        name: doctorName(d.fullName),
        specialty: d.specialty,
        state: "idle",
        onShift: false,
        load: loadByDoctor.get(d.userId) ?? 0,
        source: null,
      }));
  const freeCount = doctorBoxes.filter((d) => d.state === "free").length;
  const doctorsLoading = assignable ? availability.isLoading : directory.isLoading;
  const doctorsError = assignable ? availability.isError : directory.isError;

  return (
    <div className="space-y-5">
      <section className="rounded-[var(--radius-control)] border border-line bg-surface">
        <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1 border-b border-line px-4 py-3">
          <h2 className="font-medium text-ink">Bác sĩ có thể phân công</h2>
          <p className="text-sm text-bark" aria-live="polite">
            {selected && asked ? (
              askedPast ? (
                "Giờ khách chọn đã qua nên không xếp được nữa. Hãy đề xuất giờ khác hoặc huỷ."
              ) : (
                <>
                  Đang xếp lịch <span className="tnum font-medium text-ink">{formatTime(asked.time)}</span> ·{" "}
                  {formatDate(asked.date)}
                  {!availability.isLoading && (
                    <> — {freeCount > 0 ? `${freeCount} bác sĩ rảnh, bấm vào ô để xếp` : "không bác sĩ nào rảnh"}</>
                  )}
                </>
              )
            ) : (
              "Chọn một lịch chờ xếp bên dưới để xem bác sĩ nào rảnh. Số lịch ở mỗi ô tính theo ngày chọn bên phải."
            )}
          </p>
          <div className="ml-auto flex items-center gap-2">
            {selected && (
              <button
                type="button"
                onClick={() => setSelectedId(null)}
                className="text-sm text-ink-soft underline-offset-2 hover:underline"
              >
                Bỏ chọn
              </button>
            )}
            <input
              aria-label="Ngày xem số lịch của bác sĩ"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value || todayISO())}
              className={cn(controlClass, "h-9 w-auto")}
            />
            <Button variant="secondary" size="sm" className="whitespace-nowrap" onClick={() => setDate(todayISO())}>
              Hôm nay
            </Button>
          </div>
        </header>

        <div className="p-4">
          {doctorsLoading ? (
            <div className="flex gap-3">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-24 w-56" />
              ))}
            </div>
          ) : doctorsError ? (
            <ErrorState
              message="Không tải được danh sách bác sĩ."
              onRetry={() => (assignable ? availability.refetch() : directory.refetch())}
            />
          ) : doctorBoxes.length === 0 ? (
            <p className="text-sm text-bark">Chưa có bác sĩ nào đang làm việc.</p>
          ) : (
            <ul className="flex gap-3 overflow-x-auto pb-1">
              {doctorBoxes.map((d) => (
                <li key={d.id} className="flex shrink-0">
                  <DoctorCard
                    doctor={d}
                    asked={assignable ? asked : null}
                    dayLabel={formatDate(date).slice(0, 5)}
                    onPick={() => d.source && setConfirm(d.source)}
                  />
                </li>
              ))}
            </ul>
          )}

          {assignable && selected && !availability.isLoading && !availability.isError && freeCount === 0 && (
            <div className="mt-3 flex flex-wrap items-center gap-3 rounded-[var(--radius-control)] bg-amber-wash px-3 py-2.5 text-sm text-ink">
              Giờ này không bác sĩ nào rảnh. Chọn một giờ khác còn bác sĩ trống và gửi đề xuất cho khách.
              <Button
                size="sm"
                className="ml-auto"
                onClick={() => setDialog({ appointment: selected, mode: "propose" })}
              >
                Đề xuất giờ khác
              </Button>
            </div>
          )}
        </div>
      </section>

      <TableFrame
        title={tab === "day" ? `Lịch ngày ${formatDate(date)}` : "Lịch khách đã đặt"}
        count={rows.length}
      >
        <div className="flex gap-1 border-b border-line px-4 py-2" role="tablist">
          <TabButton active={tab === "waiting"} onClick={() => setTab("waiting")}>
            Chờ xếp bác sĩ
            {waitingRows.length > 0 && (
              <span className="ml-1.5 rounded-full bg-amber-wash px-1.5 text-xs tnum">{waitingRows.length}</span>
            )}
          </TabButton>
          <TabButton active={tab === "day"} onClick={() => setTab("day")}>
            Lịch theo ngày
          </TabButton>
        </div>

        {list.isLoading ? (
          <TableSkeleton rows={4} cols={5} />
        ) : list.isError ? (
          <div className="p-4">
            <ErrorState
              message={list.error instanceof ApiError ? list.error.message : "Không tải được lịch khám."}
              onRetry={() => list.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption={tab === "waiting" ? "Lịch chờ xếp bác sĩ" : "Lịch khám theo ngày"}
            rows={rows}
            keyOf={(a) => a.id}
            columns={columns}
            onRowClick={(a) => a.status === "AWAITING_ASSIGNMENT" && setSelectedId(a.id === selectedId ? null : a.id)}
            empty={
              tab === "waiting" ? (
                <EmptyState
                  title="Không có lịch nào đang chờ"
                  description="Khi khách gửi yêu cầu đặt lịch, nó hiện ở đây để bạn xếp bác sĩ."
                />
              ) : (
                <EmptyState title={`Không có lịch khám nào ngày ${formatDate(date)}`} />
              )
            }
          />
        )}
      </TableFrame>

      {dialog && (
        <AssignDoctorDialog
          key={`${dialog.appointment.id}-${dialog.mode}`}
          appointment={dialog.appointment}
          initialMode={dialog.mode}
          onClose={() => setDialog(null)}
        />
      )}

      <Dialog
        open={confirm !== null && selected !== null}
        onClose={() => setConfirm(null)}
        title="Xếp bác sĩ cho lịch này?"
        description={
          confirm && selected && asked
            ? `${doctorName(confirm.fullName)} · ${formatTime(asked.time)}, ${formatDate(asked.date)}`
            : undefined
        }
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirm(null)}>
              Chọn lại
            </Button>
            <Button
              loading={assign.isPending}
              onClick={() => selected && confirm && assign.mutate({ id: selected.id, doctorUserId: confirm.doctorUserId })}
            >
              Xếp và xác nhận
            </Button>
          </>
        }
      >
        <p className="text-sm text-ink-soft">
          Lịch chuyển sang “Đã xác nhận” và khách nhận email báo bác sĩ phụ trách.
        </p>
      </Dialog>

      <Dialog
        open={rejecting !== null}
        onClose={() => setRejecting(null)}
        title="Huỷ lịch này?"
        description={
          rejecting
            ? `${formatTime(askedWhen(rejecting).time)}, ${formatDate(askedWhen(rejecting).date)}`
            : undefined
        }
        footer={
          <>
            <Button variant="secondary" onClick={() => setRejecting(null)}>
              Giữ lại
            </Button>
            <Button variant="danger" loading={reject.isPending} onClick={() => rejecting && reject.mutate(rejecting.id)}>
              Huỷ lịch
            </Button>
          </>
        }
      >
        <p className="text-sm text-ink-soft">
          Khách nhận email báo phòng khám đã huỷ, và chỗ đang giữ (nếu đã đề xuất bác sĩ) được trả lại. Việc này
          không hoàn tác được.
        </p>
      </Dialog>
    </div>
  );
}

interface DoctorBox {
  id: string;
  name: string;
  specialty: string | null;
  /** idle: chưa chọn lịch nào; free/busy: rảnh hay bận đúng giờ của lịch đang chọn. */
  state: "idle" | "free" | "busy";
  onShift: boolean;
  load: number;
  source: DoctorAvailability | null;
}

function DoctorCard({
  doctor: d,
  asked,
  dayLabel,
  onPick,
}: {
  doctor: DoctorBox;
  asked: { date: string; time: string } | null;
  /** Ngày (dd/MM) mà số lịch trên ô được tính. */
  dayLabel: string;
  onPick: () => void;
}) {
  const initial = d.name.replace(/^BS\.\s*/, "").trim().charAt(0).toUpperCase() || "B";
  const clickable = d.state === "free";
  return (
    <button
      type="button"
      disabled={!clickable}
      onClick={onPick}
      className={cn(
        "flex min-h-[6.5rem] w-56 items-start gap-3 rounded-[var(--radius-control)] border p-3 text-left transition-colors",
        d.state === "free" && "border-moss/60 bg-moss-wash hover:border-moss focus-visible:border-moss",
        d.state === "busy" && "cursor-not-allowed border-line bg-paper opacity-70",
        d.state === "idle" && "cursor-default border-line bg-surface",
      )}
    >
      <span
        aria-hidden
        className="grid size-9 shrink-0 place-items-center rounded-full bg-paper text-sm font-medium text-ink-soft"
      >
        {initial}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-sm font-medium text-ink">{d.name}</span>
        {d.specialty && <span className="block truncate text-xs text-bark">{d.specialty}</span>}
        <span className="mt-1.5 flex flex-wrap gap-1">
          {d.state === "free" && asked && <Tag className="border-moss/50 text-moss">Rảnh {formatTime(asked.time)}</Tag>}
          {d.state === "busy" && asked && <Tag className="border-danger/40 text-danger">Bận {formatTime(asked.time)}</Tag>}
          {d.onShift && <Tag>Có ca trực</Tag>}
          <Tag>{d.state === "idle" ? `${d.load} lịch ngày ${dayLabel}` : `${d.load} lịch trong ngày`}</Tag>
        </span>
      </span>
    </button>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        "rounded-[var(--radius-control)] px-3 py-1.5 text-sm transition-colors",
        active ? "bg-moss text-white" : "text-ink-soft hover:bg-paper",
      )}
    >
      {children}
    </button>
  );
}
