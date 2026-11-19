"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { shiftApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils/cn";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Button, ErrorState, Skeleton } from "@/components/ui";
import type { Shift } from "@/types";

const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];

/** yyyy-MM-dd theo giờ máy (không qua toISOString để khỏi lệch ngày do múi giờ). */
function iso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Các ô của lưới tháng, bắt đầu từ thứ Hai; null là ô đệm ngoài tháng. */
function monthCells(first: Date): (string | null)[] {
  const lead = (first.getDay() + 6) % 7;
  const days = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  const cells: (string | null)[] = Array(lead).fill(null);
  for (let d = 1; d <= days; d++) cells.push(iso(new Date(first.getFullYear(), first.getMonth(), d)));
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

/**
 * Lịch làm của chính mình theo tháng: ngày nào có ca, ca mấy giờ. Dùng chung cho nhân viên và bác sĩ —
 * backend chỉ trả ca đúng người khi truyền userId của mình. Xếp ca là việc của admin nên ở đây chỉ xem.
 */
export function MyScheduleView() {
  const { userId } = useAuth();
  const [month, setMonth] = useState(() => {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), 1);
  });
  const [selected, setSelected] = useState<string>(todayISO());
  const today = todayISO();

  const range = useMemo(
    () => ({ from: iso(month), to: iso(new Date(month.getFullYear(), month.getMonth() + 1, 0)) }),
    [month],
  );
  const cells = useMemo(() => monthCells(month), [month]);

  const shifts = useQuery({
    queryKey: ["shifts", "mine", userId, range.from, range.to],
    queryFn: () => shiftApi.search(range, userId ?? undefined),
    enabled: !!userId,
  });

  const byDay = useMemo(() => {
    const map = new Map<string, Shift[]>();
    for (const s of shifts.data ?? []) {
      map.set(s.date, [...(map.get(s.date) ?? []), s].sort((a, b) => a.startTime.localeCompare(b.startTime)));
    }
    return map;
  }, [shifts.data]);

  const selectedShifts = byDay.get(selected) ?? [];
  const shiftDays = byDay.size;
  const title = `Tháng ${month.getMonth() + 1}/${month.getFullYear()}`;

  const go = (delta: number) => setMonth(new Date(month.getFullYear(), month.getMonth() + delta, 1));
  const goToday = () => {
    const now = new Date();
    setMonth(new Date(now.getFullYear(), now.getMonth(), 1));
    setSelected(todayISO());
  };

  return (
    <div className="space-y-6">
      <PageHeader title="Lịch làm của tôi" description="Các ngày và giờ bạn được xếp ca. Muốn đổi ca, liên hệ quản lý." />

      <div className="flex flex-wrap items-center gap-2">
        <Button variant="secondary" size="sm" onClick={() => go(-1)} aria-label="Tháng trước">
          <ChevronLeft aria-hidden className="size-4" />
        </Button>
        <h2 className="min-w-32 text-center text-lg font-semibold text-ink">{title}</h2>
        <Button variant="secondary" size="sm" onClick={() => go(1)} aria-label="Tháng sau">
          <ChevronRight aria-hidden className="size-4" />
        </Button>
        <Button variant="secondary" size="sm" onClick={goToday}>
          Hôm nay
        </Button>
        {shifts.data && (
          <span className="ml-auto text-sm text-bark">
            {shiftDays === 0 ? "Chưa có ca nào trong tháng này" : `${shiftDays} ngày có ca`}
          </span>
        )}
      </div>

      {shifts.isError ? (
        <ErrorState message="Không tải được lịch làm." onRetry={() => shifts.refetch()} />
      ) : (
        <div className="rounded-[var(--radius-card)] border border-line bg-surface p-2 sm:p-4">
          <div className="grid grid-cols-7 gap-1 pb-1 text-center text-xs font-medium text-bark sm:gap-2">
            {WEEKDAYS.map((w) => (
              <div key={w}>{w}</div>
            ))}
          </div>
          {shifts.isPending ? (
            <Skeleton className="h-72 w-full" />
          ) : (
            <div className="grid grid-cols-7 gap-1 sm:gap-2">
              {cells.map((day, i) => {
                if (!day) return <div key={`pad-${i}`} />;
                const list = byDay.get(day) ?? [];
                const isToday = day === today;
                const on = day === selected;
                return (
                  <button
                    key={day}
                    type="button"
                    onClick={() => setSelected(day)}
                    aria-pressed={on}
                    className={cn(
                      "flex min-h-16 flex-col items-stretch gap-1 rounded-lg border p-1 text-left transition sm:min-h-24 sm:p-2",
                      on ? "border-moss bg-moss-wash" : list.length ? "border-line bg-surface hover:border-moss" : "border-transparent bg-paper hover:border-line",
                    )}
                  >
                    <span
                      className={cn(
                        "inline-flex size-6 items-center justify-center self-start rounded-full text-xs font-semibold",
                        isToday ? "bg-moss text-white" : "text-ink",
                      )}
                    >
                      {Number(day.slice(8))}
                    </span>
                    {list.map((s) => (
                      <span
                        key={s.id}
                        className="rounded bg-moss/10 px-1 py-0.5 text-[10px] font-medium leading-tight text-moss sm:text-xs"
                      >
                        {formatTime(s.startTime)}–{formatTime(s.endTime)}
                      </span>
                    ))}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      <section className="rounded-[var(--radius-card)] border border-line bg-surface p-4">
        <h3 className="font-semibold text-ink">
          {formatDate(selected)}
          {selected === today && <span className="ml-2 text-sm font-normal text-moss">Hôm nay</span>}
        </h3>
        {selectedShifts.length === 0 ? (
          <p className="mt-1 text-sm text-bark">Không có ca làm ngày này.</p>
        ) : (
          <ul className="mt-2 space-y-2">
            {selectedShifts.map((s) => (
              <li key={s.id} className="text-sm text-ink">
                <span className="font-medium">
                  Ca {formatTime(s.startTime)}–{formatTime(s.endTime)}
                </span>
                {s.note && <span className="text-bark"> · {s.note}</span>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
