"use client";

import { useMemo, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { CalendarX, Check, Copy, Wand2 } from "lucide-react";
import { ApiError, authApi, bookingApi, leaveApi, shiftApi } from "@/lib/api";
import { formatDate } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { AUTO_SCHEDULE_MAX_PER_WEEK, AUTO_SCHEDULE_PER_DAY, autoSchedule } from "@/lib/utils/autoSchedule";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  Dialog,
  ErrorState,
  TableFrame,
  TableSkeleton,
  TextField,
  controlClass,
  useToast,
} from "@/components/ui";
import type { Leave, Shift } from "@/types";

const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];

/** Giờ làm việc cố định của phòng khám; một ca là cả ngày nên không còn chọn giờ hay ghi chú. */
const WORK_HOURS = "08:00–17:00";

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

/** Ca nửa ngày xếp từ trước khi giờ làm việc được cố định: hiện giờ của ca để admin thấy. */
function isFullDay(s: Shift): boolean {
  return s.startTime.startsWith("08:00") && s.endTime.startsWith("17:00");
}

type Person = { id: string; name: string };
type Group = { key: "DOCTOR" | "STAFF"; title: string; people: Person[] };

/** Bản xếp tự động chưa áp dụng: các ca đề xuất ("userId|ngày") và ngày nào thiếu người. */
type Draft = { keys: ReadonlySet<string>; shortfalls: { group: string; date: string; missing: number }[] };

/** Lịch khám khách đã đặt với bác sĩ vào ngày bác sĩ vừa báo nghỉ: giữ nguyên, admin phải xử lý tiếp. */
type LeaveNotice = { name: string; affected: { date: string; count: number }[] };

/** Việc đang chờ admin xác nhận trước khi bỏ ca của bác sĩ (giờ khám còn trống sẽ đóng lại). */
type Removal = { shifts: Shift[]; label: string };

/**
 * CN-39 / VD-11: lịch làm việc theo tuần dạng bảng — hàng là người, cột là ngày, bấm vào ô để xếp hoặc
 * bỏ ca. Bác sĩ và nhân viên xếp ở hai bảng riêng.
 *
 * Ca của bác sĩ mở ra giờ khám cho khách: xếp ca xong là khách đặt được ngay, bỏ ca thì những giờ còn
 * trống đóng lại (lịch đã có khách vẫn giữ). Ca của nhân viên quầy chỉ dùng để chấm công.
 */
export default function AdminShiftsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const today = iso(new Date());

  const [weekStart, setWeekStartRaw] = useState(() => mondayOf(today));
  const [draft, setDraft] = useState<Draft | null>(null);
  // Bản xem trước gắn với một tuần; đổi tuần thì bỏ, tránh áp dụng nhầm sang tuần khác.
  const setWeekStart = (week: string) => {
    setDraft(null);
    setWeekStartRaw(week);
  };
  const [removal, setRemoval] = useState<Removal | null>(null);
  // Hộp "Báo nghỉ" đang mở cho ai; hộp xác nhận huỷ báo nghỉ; thông báo lịch khám bị ảnh hưởng.
  const [leaveFor, setLeaveFor] = useState<{ group: Group; person: Person } | null>(null);
  const [cancelLeave, setCancelLeave] = useState<{ leave: Leave; label: string } | null>(null);
  const [leaveNotice, setLeaveNotice] = useState<LeaveNotice | null>(null);
  // Ô đang gửi lên server (userId|ngày), để tô mờ và chặn bấm lặp.
  const [busy, setBusy] = useState<ReadonlySet<string>>(new Set());

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
  const leaves = useQuery({
    queryKey: ["leaves", weekStart],
    queryFn: () => leaveApi.search({ from: weekStart, to: weekEnd }),
    placeholderData: keepPreviousData,
  });
  const doctors = useQuery({
    queryKey: ["admin-users", { role: "DOCTOR", status: "ACTIVE", size: 100 }],
    queryFn: () => authApi.listUsers({ role: "DOCTOR", status: "ACTIVE", size: 100 }),
  });
  const staff = useQuery({
    queryKey: ["admin-users", { role: "STAFF", status: "ACTIVE", size: 100 }],
    queryFn: () => authApi.listUsers({ role: "STAFF", status: "ACTIVE", size: 100 }),
  });

  const toPeople = (users: { id: string; firstName: string; lastName: string }[] | undefined): Person[] =>
    (users ?? [])
      .map((u) => ({ id: u.id, name: `${u.firstName} ${u.lastName}`.trim() }))
      .sort((a, b) => a.name.localeCompare(b.name, "vi"));

  const groups: Group[] = [
    { key: "DOCTOR", title: "Bác sĩ", people: toPeople(doctors.data?.content) },
    { key: "STAFF", title: "Nhân viên", people: toPeople(staff.data?.content) },
  ];

  // userId → ngày → các ca (thường một ca cả ngày; có thể nhiều ca nếu xếp từ trước khi cố định giờ).
  const cells = useMemo(() => {
    const map = new Map<string, Shift[]>();
    for (const s of shifts.data ?? []) {
      const key = `${s.userId}|${s.date}`;
      map.set(key, [...(map.get(key) ?? []), s]);
    }
    return map;
  }, [shifts.data]);

  // userId|ngày → ngày nghỉ đã ghi.
  const leaveCells = useMemo(() => {
    const map = new Map<string, Leave>();
    for (const l of leaves.data ?? []) map.set(`${l.userId}|${l.date}`, l);
    return map;
  }, [leaves.data]);

  const withBusy = async (keys: string[], job: () => Promise<unknown>) => {
    setBusy((cur) => new Set([...cur, ...keys]));
    try {
      await job();
      await qc.invalidateQueries({ queryKey: ["shifts"] });
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không cập nhật được lịch làm việc.");
    } finally {
      setBusy((cur) => {
        const next = new Set(cur);
        keys.forEach((k) => next.delete(k));
        return next;
      });
    }
  };

  const addShifts = (userId: string, dates: string[]) =>
    withBusy(dates.map((d) => `${userId}|${d}`), () => shiftApi.create({ userId, dates }));

  const removeShifts = (list: Shift[]) =>
    withBusy(list.map((s) => `${s.userId}|${s.date}`), () => Promise.all(list.map((s) => shiftApi.remove(s.id))));

  /** Bỏ ca của nhân viên thì xoá luôn; của bác sĩ thì hỏi lại vì giờ khám còn trống sẽ đóng. */
  const requestRemoval = (group: Group, list: Shift[], label: string) => {
    if (list.length === 0) return;
    if (group.key === "DOCTOR") setRemoval({ shifts: list, label });
    else void removeShifts(list);
  };

  const toggle = (group: Group, person: Person, day: string) => {
    const key = `${person.id}|${day}`;
    const onLeave = leaveCells.get(key);
    if (onLeave) {
      setCancelLeave({ leave: onLeave, label: `${person.name} · ${formatDate(day)}` });
      return;
    }
    const existing = cells.get(key) ?? [];
    if (existing.length > 0) {
      requestRemoval(group, existing, `${person.name} · ${formatDate(day)}`);
    } else if (draft) {
      // Đang xem bản tự động xếp: bấm ô trống là sửa bản nháp, chưa ghi gì lên server.
      const keys = new Set(draft.keys);
      if (!keys.delete(key)) keys.add(key);
      setDraft({ ...draft, keys });
    } else {
      void addShifts(person.id, [day]);
    }
  };

  /** Dựng bản xếp tự động: mỗi ngày đủ 2 bác sĩ và 2 nhân viên, chia đều theo ca của 4 tuần trước. */
  const plan = useMutation({
    mutationFn: async () => {
      const past = await shiftApi.search({ from: addDays(weekStart, -28), to: addDays(weekStart, -1) });
      const history = new Set(past.map((s) => `${s.userId}|${s.date}`));
      const existing = new Set(cells.keys());
      const keys = new Set<string>();
      const shortfalls: Draft["shortfalls"] = [];
      for (const group of groups) {
        const result = autoSchedule({
          days: weekDays,
          today,
          people: group.people,
          existing,
          history,
          unavailable: new Set(leaveCells.keys()),
        });
        result.assigned.forEach((k) => keys.add(k));
        result.shortfalls.forEach((x) => shortfalls.push({ group: group.title, ...x }));
      }
      return { keys, shortfalls } satisfies Draft;
    },
    onSuccess: (result) => {
      setDraft(result);
      if (result.keys.size === 0 && result.shortfalls.length === 0) {
        toast.success("Tuần này đã đủ người mỗi ngày, không cần xếp thêm.");
      }
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không dựng được bản xếp tự động."),
  });

  const applyDraft = useMutation({
    mutationFn: async (d: Draft) => {
      const byUser = new Map<string, string[]>();
      for (const key of d.keys) {
        const [userId, date] = key.split("|");
        byUser.set(userId, [...(byUser.get(userId) ?? []), date]);
      }
      for (const [userId, dates] of byUser) await shiftApi.create({ userId, dates });
      return d.keys.size;
    },
    onSuccess: (count) => {
      qc.invalidateQueries({ queryKey: ["shifts"] });
      setDraft(null);
      toast.success(`Đã xếp ${count} ca`);
    },
    onError: (err) => {
      // Có thể đã xếp được một phần: tải lại để bảng phản ánh đúng, bản nháp giữ để bấm áp dụng lại.
      qc.invalidateQueries({ queryKey: ["shifts"] });
      toast.error(err instanceof ApiError ? err.message : "Không áp dụng được bản xếp.");
    },
  });

  const copyLastWeek = useMutation({
    mutationFn: async () => {
      const previous = await shiftApi.search({ from: addDays(weekStart, -7), to: addDays(weekStart, -1) });
      // Gom theo người và khung giờ để mỗi người chỉ một lần gọi. Ngày đã qua của tuần này thì bỏ.
      const batches = new Map<string, { userId: string; start: string; end: string; dates: string[] }>();
      for (const s of previous) {
        const date = addDays(s.date, 7);
        // Ngày đã qua, và ngày người đó đã báo nghỉ ở tuần này thì không chép.
        if (date < today || leaveCells.has(`${s.userId}|${date}`)) continue;
        const key = `${s.userId}|${s.startTime}|${s.endTime}`;
        const batch = batches.get(key) ?? { userId: s.userId, start: s.startTime, end: s.endTime, dates: [] };
        batch.dates.push(date);
        batches.set(key, batch);
      }
      let created = 0;
      for (const b of batches.values()) {
        const result = await shiftApi.create({ userId: b.userId, dates: b.dates, startTime: b.start, endTime: b.end });
        created += result.created.length;
      }
      return { found: previous.length, created };
    },
    onSuccess: ({ found, created }) => {
      qc.invalidateQueries({ queryKey: ["shifts"] });
      if (found === 0) toast.error("Tuần trước chưa có ca nào để sao chép.");
      else if (created === 0) toast.success("Tuần này đã có đủ các ca của tuần trước.");
      else toast.success(`Đã sao chép ${created} ca từ tuần trước`);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không sao chép được tuần trước."),
  });

  const removeLeave = useMutation({
    mutationFn: (leave: Leave) => leaveApi.remove(leave.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["leaves"] });
      toast.success("Đã huỷ báo nghỉ, ngày này xếp ca lại được");
      setCancelLeave(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không huỷ được báo nghỉ."),
  });

  const loading = shifts.isLoading || leaves.isLoading || doctors.isLoading || staff.isLoading;
  const failed = shifts.isError || leaves.isError || doctors.isError || staff.isError;

  return (
    <>
      <PageHeader
        title="Lịch làm việc"
        description={`Giờ làm việc cố định ${WORK_HOURS}. Bấm vào ô để xếp hoặc bỏ ca của người đó trong ngày. Bác sĩ có ca ngày nào thì ngày đó khách mới đặt được lịch khám.`}
        actions={
          <>
            <Button variant="secondary" loading={copyLastWeek.isPending} onClick={() => copyLastWeek.mutate()}>
              <Copy aria-hidden className="size-4" />
              Sao chép tuần trước
            </Button>
            <Button loading={plan.isPending} disabled={loading || failed} onClick={() => plan.mutate()}>
              <Wand2 aria-hidden className="size-4" />
              Tự động xếp tuần
            </Button>
          </>
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
        <Button variant="secondary" onClick={() => setWeekStart(mondayOf(today))}>
          Tuần này
        </Button>
        <Button variant="secondary" onClick={() => setWeekStart(addDays(weekStart, 7))}>
          Tuần sau
        </Button>
        <p className="text-sm text-bark">
          {formatDate(weekStart)} – {formatDate(weekEnd)}
        </p>
      </div>

      {failed ? (
        <ErrorState
          message={
            shifts.error instanceof ApiError ? shifts.error.message : "Không tải được lịch làm việc."
          }
          onRetry={() => {
            void shifts.refetch();
            void leaves.refetch();
            void doctors.refetch();
            void staff.refetch();
          }}
        />
      ) : loading ? (
        <TableFrame title="Lịch làm việc">
          <TableSkeleton rows={5} cols={4} />
        </TableFrame>
      ) : (
        <div className="space-y-6">
          {draft && (
            <DraftBanner
              draft={draft}
              applying={applyDraft.isPending}
              onApply={() => applyDraft.mutate(draft)}
              onDiscard={() => setDraft(null)}
            />
          )}
          {groups.map((group) => (
            <ShiftGrid
              key={group.key}
              group={group}
              draft={draft?.keys ?? null}
              weekDays={weekDays}
              today={today}
              cells={cells}
              leaveCells={leaveCells}
              onReportLeave={(person) => setLeaveFor({ group, person })}
              busy={busy}
              onToggle={(person, day) => toggle(group, person, day)}
              onFillWeek={(person) =>
                void addShifts(
                  person.id,
                  // Thứ 2–thứ 6, bỏ ngày đã qua và ngày đã có ca.
                  weekDays
                    .slice(0, 5)
                    .filter((d) => d >= today && !cells.has(`${person.id}|${d}`) && !leaveCells.has(`${person.id}|${d}`)),
                )
              }
              onClearWeek={(person) =>
                requestRemoval(
                  group,
                  weekDays.flatMap((d) => (d >= today ? (cells.get(`${person.id}|${d}`) ?? []) : [])),
                  `${person.name} · cả tuần ${formatDate(weekStart)} – ${formatDate(weekEnd)}`,
                )
              }
            />
          ))}
        </div>
      )}

      {leaveFor && (
        <LeaveDialog
          group={leaveFor.group}
          person={leaveFor.person}
          weekDays={weekDays}
          today={today}
          leaveCells={leaveCells}
          onClose={() => setLeaveFor(null)}
          onDone={(notice) => {
            setLeaveFor(null);
            if (notice.affected.length > 0) setLeaveNotice(notice);
          }}
        />
      )}

      <Dialog
        open={cancelLeave !== null}
        onClose={() => setCancelLeave(null)}
        title="Huỷ báo nghỉ?"
        description={cancelLeave?.label}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCancelLeave(null)}>
              Giữ báo nghỉ
            </Button>
            <Button
              loading={removeLeave.isPending}
              onClick={() => cancelLeave && removeLeave.mutate(cancelLeave.leave)}
            >
              Huỷ báo nghỉ
            </Button>
          </>
        }
      >
        <p className="text-sm text-ink-soft">
          {cancelLeave?.leave.reason ? `Lý do đã ghi: ${cancelLeave.leave.reason}. ` : ""}
          Huỷ thì ô này trở lại trống, chưa có ca; bạn xếp ca lại nếu cần.
        </p>
      </Dialog>

      <Dialog
        open={leaveNotice !== null}
        onClose={() => setLeaveNotice(null)}
        title="Bác sĩ còn lịch khám trong ngày nghỉ"
        description={leaveNotice?.name}
        footer={
          <>
            <Button variant="secondary" onClick={() => setLeaveNotice(null)}>
              Để sau
            </Button>
            <Link
              href="/admin/appointments"
              className="inline-flex h-9 items-center rounded-[var(--radius-control)] bg-moss px-4 text-sm font-medium text-white"
            >
              Mở lịch khám
            </Link>
          </>
        }
      >
        <p className="text-sm text-ink-soft">
          Giờ khám còn trống đã đóng, nhưng lịch khách đã đặt vẫn giữ nguyên và chưa có bác sĩ thay. Hãy liên hệ khách
          hoặc xếp bác sĩ khác:
        </p>
        <ul className="mt-2 list-disc pl-5 text-sm text-ink">
          {leaveNotice?.affected.map((a) => (
            <li key={a.date}>
              {formatDate(a.date)}: {a.count} lịch khám
            </li>
          ))}
        </ul>
      </Dialog>

      <Dialog
        open={removal !== null}
        onClose={() => setRemoval(null)}
        title="Bỏ ca của bác sĩ?"
        description={removal?.label}
        footer={
          <>
            <Button variant="secondary" onClick={() => setRemoval(null)}>
              Huỷ
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                const list = removal?.shifts ?? [];
                setRemoval(null);
                void removeShifts(list);
              }}
            >
              Bỏ ca
            </Button>
          </>
        }
      >
        <p className="text-sm text-ink-soft">
          Những giờ khám còn trống trong ca sẽ đóng lại, khách không đặt vào đó được nữa. Lịch khách đã đặt vẫn
          giữ nguyên — muốn huỷ thì phải liên hệ khách trước.
        </p>
      </Dialog>
    </>
  );
}

function DraftBanner({
  draft,
  applying,
  onApply,
  onDiscard,
}: {
  draft: Draft;
  applying: boolean;
  onApply: () => void;
  onDiscard: () => void;
}) {
  return (
    <div role="status" className="rounded-[var(--radius-control)] border border-dashed border-moss bg-moss/5 p-4">
      <p className="font-medium text-ink">
        Bản xếp tự động chưa áp dụng: {draft.keys.size} ca mới
      </p>
      <p className="mt-1 text-sm text-bark">
        Mỗi ngày {AUTO_SCHEDULE_PER_DAY} bác sĩ và {AUTO_SCHEDULE_PER_DAY} nhân viên, mỗi người tối đa{" "}
        {AUTO_SCHEDULE_MAX_PER_WEEK} ngày một tuần. Ô viền nét đứt là ca đề xuất: bấm vào để bỏ hoặc thêm, rồi
        bấm Áp dụng.
      </p>
      {draft.shortfalls.length > 0 && (
        <ul className="mt-2 list-disc pl-5 text-sm text-danger">
          {draft.shortfalls.map((x) => (
            <li key={`${x.group}|${x.date}`}>
              {formatDate(x.date)} còn thiếu {x.missing} {x.group.toLowerCase()} (không còn ai xếp thêm được)
            </li>
          ))}
        </ul>
      )}
      <div className="mt-3 flex gap-2">
        <Button loading={applying} disabled={draft.keys.size === 0} onClick={onApply}>
          Áp dụng
        </Button>
        <Button variant="secondary" disabled={applying} onClick={onDiscard}>
          Bỏ bản này
        </Button>
      </div>
    </div>
  );
}

function ShiftGrid({
  group,
  draft,
  weekDays,
  today,
  cells,
  leaveCells,
  onReportLeave,
  busy,
  onToggle,
  onFillWeek,
  onClearWeek,
}: {
  group: Group;
  draft: ReadonlySet<string> | null;
  weekDays: string[];
  today: string;
  cells: ReadonlyMap<string, Shift[]>;
  leaveCells: ReadonlyMap<string, Leave>;
  onReportLeave: (person: Person) => void;
  busy: ReadonlySet<string>;
  onToggle: (person: Person, day: string) => void;
  onFillWeek: (person: Person) => void;
  onClearWeek: (person: Person) => void;
}) {
  const perDay = weekDays.map(
    (d) => group.people.filter((p) => cells.has(`${p.id}|${d}`) || draft?.has(`${p.id}|${d}`)).length,
  );

  return (
    <TableFrame title={group.title} count={group.people.length}>
      {group.people.length === 0 ? (
        <p className="px-4 py-8 text-center text-sm text-bark">
          Chưa có {group.title.toLowerCase()} nào đang hoạt động.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] border-collapse text-sm">
            <caption className="sr-only">Lịch làm việc của {group.title.toLowerCase()} trong tuần</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs text-bark">
                <th scope="col" className="sticky left-0 bg-surface px-4 py-2 font-medium">
                  Người
                </th>
                {weekDays.map((d, i) => (
                  <th
                    key={d}
                    scope="col"
                    className={cn("px-1 py-2 text-center font-medium", d === today && "text-moss")}
                  >
                    <span className="block">{WEEKDAYS[i]}</span>
                    <span className="tnum block font-normal">
                      {d.slice(8)}/{d.slice(5, 7)}
                    </span>
                  </th>
                ))}
                <th scope="col" className="px-4 py-2 text-right font-medium">
                  Nhanh
                </th>
              </tr>
            </thead>
            <tbody>
              {group.people.map((person) => (
                <tr key={person.id} className="border-b border-line last:border-b-0">
                  <th
                    scope="row"
                    className="sticky left-0 whitespace-nowrap bg-surface px-4 py-1.5 text-left font-medium text-ink"
                  >
                    {group.key === "DOCTOR" ? "BS. " : ""}
                    {person.name}
                  </th>
                  {weekDays.map((day) => {
                    const list = cells.get(`${person.id}|${day}`) ?? [];
                    const leave = leaveCells.get(`${person.id}|${day}`);
                    const real = list.length > 0;
                    const proposed = !real && (draft?.has(`${person.id}|${day}`) ?? false);
                    const on = real || proposed;
                    const past = day < today;
                    const pending = busy.has(`${person.id}|${day}`);
                    return (
                      <td key={day} className="px-1 py-1">
                        <button
                          type="button"
                          aria-pressed={on}
                          aria-label={`${person.name}, ${formatDate(day)}: ${leave ? "đã báo nghỉ" : real ? "đang có ca" : proposed ? "ca đề xuất" : "chưa có ca"}`}
                          title={leave ? `Nghỉ${leave.reason ? `: ${leave.reason}` : ""}` : undefined}
                          disabled={past || pending}
                          onClick={() => onToggle(person, day)}
                          className={cn(
                            "grid h-10 w-full min-w-[3.25rem] place-items-center rounded-[var(--radius-control)] border text-xs transition",
                            leave && "border-[#f0c9a4] bg-[#fff1e6] font-medium text-[#c2570c] hover:bg-[#ffe6d2]",
                            real && "border-moss bg-moss text-white hover:opacity-90",
                            proposed && "border-dashed border-moss bg-moss/10 text-moss hover:bg-moss/20",
                            !on && !leave && "border-line bg-surface text-bark hover:border-moss hover:bg-moss/10",
                            (past || pending) && "cursor-not-allowed opacity-50",
                          )}
                        >
                          {leave ? (
                            "Nghỉ"
                          ) : proposed ? (
                            <Check aria-hidden className="size-4" />
                          ) : real ? (
                            list.every(isFullDay) ? (
                              <Check aria-hidden className="size-4" />
                            ) : (
                              // Ca nửa ngày xếp từ trước: hiện giờ thật thay vì dấu tích.
                              <span className="tnum leading-tight">
                                {list.map((s) => `${s.startTime.slice(0, 2)}–${s.endTime.slice(0, 2)}`).join(" ")}
                              </span>
                            )
                          ) : null}
                        </button>
                      </td>
                    );
                  })}
                  <td className="whitespace-nowrap px-4 py-1 text-right">
                    <Button variant="ghost" size="sm" onClick={() => onReportLeave(person)}>
                      <CalendarX aria-hidden className="size-3.5" />
                      Báo nghỉ
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => onFillWeek(person)}>
                      T2–T6
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => onClearWeek(person)}>
                      Bỏ tuần
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-line bg-paper text-xs">
                <th scope="row" className="sticky left-0 bg-paper px-4 py-2 text-left font-medium text-bark">
                  Số người trực
                </th>
                {perDay.map((n, i) => (
                  <td
                    key={weekDays[i]}
                    className={cn(
                      "tnum px-1 py-2 text-center font-medium",
                      // Ngày không có bác sĩ nào thì khách không đặt được lịch khám: nhắc admin.
                      n === 0 && group.key === "DOCTOR" && weekDays[i] >= today ? "text-danger" : "text-ink",
                    )}
                  >
                    {n}
                  </td>
                ))}
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </TableFrame>
  );
}

/**
 * Admin ghi tay ngày nghỉ khi có người báo nghỉ: chọn các ngày trong tuần đang xem và lý do (không bắt buộc).
 * Ca đã xếp của người đó trong các ngày này bị gỡ. Với bác sĩ, báo lại luôn những lịch khách đã đặt hôm đó,
 * vì lịch đã đặt vẫn giữ nguyên và cần admin xử lý.
 */
function LeaveDialog({
  group,
  person,
  weekDays,
  today,
  leaveCells,
  onClose,
  onDone,
}: {
  group: Group;
  person: Person;
  weekDays: string[];
  today: string;
  leaveCells: ReadonlyMap<string, Leave>;
  onClose: () => void;
  onDone: (notice: LeaveNotice) => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const [days, setDays] = useState<string[]>([]);
  const [reason, setReason] = useState("");

  const save = useMutation({
    mutationFn: async () => {
      const result = await leaveApi.create({ userId: person.id, dates: days, reason: reason.trim() || undefined });
      const affected: LeaveNotice["affected"] = [];
      if (group.key === "DOCTOR") {
        for (const date of result.created.map((l) => l.date)) {
          try {
            const list = await bookingApi.search({ date, doctorUserId: person.id });
            const count = list.filter((a) => ["PENDING", "AWAITING_CUSTOMER", "CONFIRMED"].includes(a.status)).length;
            if (count > 0) affected.push({ date, count });
          } catch {
            // Không tra được lịch khám thì thôi: ghi nghỉ đã xong, admin vẫn xem được ở màn Lịch khám.
          }
        }
      }
      return { result, affected };
    },
    onSuccess: ({ result, affected }) => {
      qc.invalidateQueries({ queryKey: ["leaves"] });
      qc.invalidateQueries({ queryKey: ["shifts"] });
      toast.success(
        `Đã ghi nghỉ ${result.created.length} ngày cho ${person.name}` +
          (result.removedShifts > 0 ? `, gỡ ${result.removedShifts} ca đã xếp` : ""),
      );
      onDone({ name: `BS. ${person.name}`, affected });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không ghi được ngày nghỉ."),
  });

  return (
    <Dialog
      open
      onClose={onClose}
      title="Báo nghỉ"
      description={`${group.key === "DOCTOR" ? "BS. " : ""}${person.name}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button loading={save.isPending} disabled={days.length === 0} onClick={() => save.mutate()}>
            Ghi nghỉ
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-ink">Ngày nghỉ trong tuần đang xem</legend>
          <div className="flex flex-wrap gap-2">
            {weekDays.map((day, index) => {
              const already = leaveCells.has(`${person.id}|${day}`);
              const checked = days.includes(day);
              const disabled = already || day < today;
              return (
                <label
                  key={day}
                  className={cn(
                    "rounded-[var(--radius-control)] border px-3 py-1.5 text-sm",
                    disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer",
                    checked ? "border-moss bg-moss text-white" : "border-line-strong bg-surface text-ink-soft",
                  )}
                >
                  <input
                    type="checkbox"
                    className="sr-only"
                    disabled={disabled}
                    checked={checked}
                    onChange={() =>
                      setDays((cur) => (cur.includes(day) ? cur.filter((d) => d !== day) : [...cur, day]))
                    }
                  />
                  {WEEKDAYS[index]}
                  <span className="ml-1.5 text-xs opacity-80">
                    {day.slice(8)}/{day.slice(5, 7)}
                  </span>
                  {already && <span className="ml-1.5 text-xs">đã nghỉ</span>}
                </label>
              );
            })}
          </div>
        </fieldset>
        <TextField
          label="Lý do (không bắt buộc)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Ví dụ: ốm, việc gia đình"
        />
        <p className="text-sm text-bark">
          Ca đã xếp của người này trong các ngày nghỉ sẽ bị gỡ, và ngày nghỉ không xếp ca được cho tới khi bạn huỷ báo
          nghỉ.
        </p>
      </div>
    </Dialog>
  );
}
