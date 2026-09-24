"use client";

import { useMemo, useState, type FormEvent } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarPlus, Trash2 } from "lucide-react";
import { ApiError, authApi, shiftApi } from "@/lib/api";
import { formatDate } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  DataTable,
  Dialog,
  EmptyState,
  ErrorState,
  SelectField,
  TableFrame,
  TableSkeleton,
  TextField,
  controlClass,
  useToast,
  type Column,
} from "@/components/ui";
import type { Shift } from "@/types";

const WEEKDAYS = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];

function iso(date: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
}

/** Thứ 2 của tuần chứa ngày đang xem. Tuần làm việc ở Việt Nam bắt đầu từ thứ 2. */
function mondayOf(dateIso: string): string {
  const [y, m, d] = dateIso.split("-").map(Number);
  const date = new Date(y, m - 1, d);
  const shift = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - shift);
  return iso(date);
}

function addDays(dateIso: string, days: number): string {
  const [y, m, d] = dateIso.split("-").map(Number);
  return iso(new Date(y, m - 1, d + days));
}

/**
 * CN-39 / VD-11: xếp ca trực theo tuần.
 *
 * Ca của bác sĩ mở ra giờ khám cho khách: xếp ca xong là khách đặt được ngay, bỏ ca thì những
 * giờ còn trống đóng lại (lịch đã có khách vẫn giữ). Nhân viên quầy cũng xếp ca được, nhưng ca
 * của họ chỉ dùng để chấm công.
 */
export default function AdminShiftsPage() {
  const qc = useQueryClient();
  const toast = useToast();

  const [weekStart, setWeekStart] = useState(() => mondayOf(iso(new Date())));
  const [creating, setCreating] = useState(false);
  const [removing, setRemoving] = useState<Shift | null>(null);

  const weekEnd = addDays(weekStart, 6);
  const weekDays = useMemo(
    () => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)),
    [weekStart],
  );

  const shifts = useQuery({
    queryKey: ["shifts", weekStart],
    queryFn: () => shiftApi.search({ from: weekStart, to: weekEnd }),
    placeholderData: keepPreviousData,
  });
  const people = useQuery({
    queryKey: ["admin-users", { size: 100 }],
    queryFn: () => authApi.listUsers({ size: 100 }),
  });

  // Chỉ người đi làm mới có ca; khách hàng không liên quan.
  const staffList = (people.data?.content ?? []).filter((u) =>
    u.roles.some((r) => r === "DOCTOR" || r === "STAFF"),
  );
  const nameOf = (userId: string) => {
    const user = people.data?.content.find((u) => u.id === userId);
    if (!user) return "Chưa rõ tên";
    const role = user.roles.includes("DOCTOR") ? "BS. " : "";
    return `${role}${user.firstName} ${user.lastName}`.trim();
  };

  const remove = useMutation({
    mutationFn: (shift: Shift) => shiftApi.remove(shift.id),
    onSuccess: (_, shift) => {
      qc.invalidateQueries({ queryKey: ["shifts"] });
      toast.success(`Đã bỏ ca ngày ${formatDate(shift.date)} của ${nameOf(shift.userId)}`);
      setRemoving(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không bỏ được ca."),
  });

  const columns: Column<Shift>[] = [
    { key: "date", header: "Ngày", cell: (s) => <span className="tnum">{formatDate(s.date)}</span> },
    { key: "person", header: "Người", cell: (s) => <span className="font-medium text-ink">{nameOf(s.userId)}</span> },
    {
      key: "time",
      header: "Giờ",
      numeric: true,
      cell: (s) => <span className="tnum">{`${s.startTime.slice(0, 5)}–${s.endTime.slice(0, 5)}`}</span>,
    },
    { key: "note", header: "Ghi chú", hideBelow: "lg", cell: (s) => <span className="text-bark">{s.note || "—"}</span> },
    {
      key: "actions",
      header: "",
      cell: (s) => (
        <div className="flex justify-end">
          <Button variant="ghost" size="sm" onClick={() => setRemoving(s)}>
            <Trash2 aria-hidden className="size-3.5" />
            Bỏ ca
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Lịch làm việc"
        description="Bác sĩ có ca ngày nào thì ngày đó khách mới đặt được lịch khám."
        actions={
          <Button onClick={() => setCreating(true)} disabled={staffList.length === 0}>
            <CalendarPlus aria-hidden className="size-4" />
            Xếp ca
          </Button>
        }
      />

      <div className="mb-5 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="week" className="mb-1.5 block text-sm font-medium">
            Tuần bắt đầu
          </label>
          <input
            id="week"
            type="date"
            value={weekStart}
            onChange={(e) => e.target.value && setWeekStart(mondayOf(e.target.value))}
            className={`${controlClass} h-9`}
          />
        </div>
        <Button variant="secondary" onClick={() => setWeekStart(addDays(weekStart, -7))}>
          Tuần trước
        </Button>
        <Button variant="secondary" onClick={() => setWeekStart(addDays(weekStart, 7))}>
          Tuần sau
        </Button>
        <p className="text-sm text-bark">
          {formatDate(weekStart)} – {formatDate(weekEnd)}
        </p>
      </div>

      <TableFrame title="Ca trực trong tuần" count={shifts.data?.length}>
        {shifts.isLoading || people.isLoading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : shifts.isError ? (
          <div className="p-4">
            <ErrorState
              message={shifts.error instanceof ApiError ? shifts.error.message : "Không tải được lịch làm việc."}
              onRetry={() => shifts.refetch()}
            />
          </div>
        ) : (
          <DataTable
            caption="Ca trực trong tuần"
            rows={shifts.data ?? []}
            keyOf={(s) => s.id}
            columns={columns}
            empty={
              <EmptyState
                title="Tuần này chưa xếp ca nào"
                description="Chưa có ca thì tuần này khách không đặt được lịch khám."
                action={<Button onClick={() => setCreating(true)}>Xếp ca</Button>}
              />
            }
          />
        )}
      </TableFrame>

      <ShiftFormDialog
        open={creating}
        onClose={() => setCreating(false)}
        weekDays={weekDays}
        people={staffList.map((u) => ({ id: u.id, label: nameOf(u.id) }))}
      />

      <Dialog
        open={removing !== null}
        onClose={() => setRemoving(null)}
        title="Bỏ ca trực này?"
        description={removing ? `${nameOf(removing.userId)} · ${formatDate(removing.date)}` : undefined}
        footer={
          <>
            <Button variant="secondary" onClick={() => setRemoving(null)}>
              Huỷ
            </Button>
            <Button variant="danger" loading={remove.isPending} onClick={() => removing && remove.mutate(removing)}>
              Bỏ ca
            </Button>
          </>
        }
      >
        <p className="text-sm text-ink-soft">
          Những giờ khám còn trống trong ca sẽ đóng lại, khách không đặt vào đó được nữa. Lịch khách
          đã đặt vẫn giữ nguyên — muốn huỷ thì phải liên hệ khách trước.
        </p>
      </Dialog>
    </>
  );
}

function ShiftFormDialog({
  open,
  onClose,
  weekDays,
  people,
}: {
  open: boolean;
  onClose: () => void;
  weekDays: string[];
  people: { id: string; label: string }[];
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const [userId, setUserId] = useState("");
  const [days, setDays] = useState<string[]>([]);
  const [startTime, setStartTime] = useState("08:00");
  const [endTime, setEndTime] = useState("12:00");
  const [note, setNote] = useState("");

  const create = useMutation({
    mutationFn: () =>
      shiftApi.create({
        userId: userId || people[0]?.id,
        dates: days,
        startTime: `${startTime}:00`,
        endTime: `${endTime}:00`,
        note: note || undefined,
      }),
    onSuccess: (result) => {
      qc.invalidateQueries({ queryKey: ["shifts"] });
      toast.success(
        result.skipped.length
          ? `Đã xếp ${result.created.length} ngày; ${result.skipped.length} ngày đã có ca này từ trước`
          : `Đã xếp ca cho ${result.created.length} ngày`,
      );
      setDays([]);
      setNote("");
      onClose();
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không xếp được ca."),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (days.length === 0) {
      toast.error("Chọn ít nhất một ngày trong tuần.");
      return;
    }
    if (endTime <= startTime) {
      toast.error("Giờ kết thúc phải sau giờ bắt đầu.");
      return;
    }
    create.mutate();
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Xếp ca trực"
      description="Chọn nhiều ngày để xếp cả tuần trong một lần."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button form="shift-form" type="submit" loading={create.isPending}>
            Xếp ca
          </Button>
        </>
      }
    >
      <form id="shift-form" onSubmit={submit} className="space-y-4">
        <SelectField label="Người trực" required value={userId} onChange={(e) => setUserId(e.target.value)}>
          {people.map((p) => (
            <option key={p.id} value={p.id}>
              {p.label}
            </option>
          ))}
        </SelectField>

        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-ink">Ngày trong tuần</legend>
          <div className="flex flex-wrap gap-2">
            {weekDays.map((day, index) => {
              const checked = days.includes(day);
              return (
                <label
                  key={day}
                  className={`cursor-pointer rounded-[var(--radius-control)] border px-3 py-1.5 text-sm ${
                    checked ? "border-moss bg-moss text-white" : "border-line-strong bg-surface text-ink-soft"
                  }`}
                >
                  <input
                    type="checkbox"
                    className="sr-only"
                    checked={checked}
                    onChange={() =>
                      setDays((current) =>
                        current.includes(day) ? current.filter((d) => d !== day) : [...current, day],
                      )
                    }
                  />
                  {WEEKDAYS[index]}
                  <span className="ml-1.5 text-xs opacity-80">{day.slice(8)}/{day.slice(5, 7)}</span>
                </label>
              );
            })}
          </div>
        </fieldset>

        <div className="grid gap-3 sm:grid-cols-2">
          <TextField
            label="Bắt đầu"
            type="time"
            required
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
          />
          <TextField label="Kết thúc" type="time" required value={endTime} onChange={(e) => setEndTime(e.target.value)} />
        </div>

        <TextField label="Ghi chú" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ca sáng" />
      </form>
    </Dialog>
  );
}
