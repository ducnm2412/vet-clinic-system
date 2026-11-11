"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { TriangleAlert } from "lucide-react";
import { ApiError, bookingApi } from "@/lib/api";
import { cn } from "@/lib/utils/cn";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import type { Appointment, DoctorAvailability } from "@/types";
import { Button, Dialog, ErrorState, SelectField, Tag, controlClass, useToast } from "@/components/ui";

/** Phòng khám nhận yêu cầu trước tối đa bấy nhiêu ngày (khớp REQUEST_HORIZON_DAYS ở backend). */
const HORIZON_DAYS = 30;

function addDaysISO(iso: string, n: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + n);
  const p = (v: number) => String(v).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export type Mode = "assign" | "propose";

/**
 * Admin xếp bác sĩ cho một yêu cầu đặt lịch.
 *
 * Hai cách: xếp đúng giờ khách chọn (lịch thành "Đã xác nhận" ngay), hoặc — khi giờ đó không bác sĩ
 * nào rảnh — đề xuất giờ khác kèm một bác sĩ rảnh giờ đó; khách đồng ý hoặc huỷ. Bác sĩ đang bận
 * vẫn hiện trong danh sách nhưng không chọn được, để admin thấy rõ vì sao.
 *
 * Gắn `key={appointment.id}` ở chỗ gọi để mỗi lần mở là một trạng thái mới.
 */
export function AssignDoctorDialog({
  appointment,
  initialMode = "assign",
  onClose,
}: {
  appointment: Appointment;
  /** Mở sẵn tab nào; lịch đã có đề xuất luôn mở ở "propose". */
  initialMode?: Mode;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const toast = useToast();

  // Lịch đã có đề xuất chỉ còn việc đổi đề xuất; lịch mới có hai lựa chọn.
  const canAssign = appointment.status === "AWAITING_ASSIGNMENT";
  const [mode, setMode] = useState<Mode>(canAssign ? initialMode : "propose");

  // Giờ khách chọn ban đầu. Lịch chờ khách đồng ý đã mang giờ đề xuất ở `date`/`startTime`.
  const askedDate = appointment.preferredDate ?? appointment.date;
  const askedTime = appointment.preferredStartTime ?? appointment.startTime;

  const [date, setDate] = useState(askedDate >= todayISO() ? askedDate : todayISO());
  const [time, setTime] = useState("");
  const [doctorId, setDoctorId] = useState("");

  const detail = useQuery({
    queryKey: ["appointment-detail", appointment.id],
    queryFn: () => bookingApi.byId(appointment.id),
  });

  const hours = useQuery({
    queryKey: ["clinic-hours", date],
    queryFn: () => bookingApi.clinicHours(date),
    enabled: mode === "propose" && date !== "",
  });

  const when = mode === "propose" ? (time ? { date, time } : null) : undefined;
  const doctors = useQuery({
    queryKey: ["available-doctors", appointment.id, mode, when?.date ?? "", when?.time ?? ""],
    queryFn: () => bookingApi.availableDoctors(appointment.id, when ?? undefined),
    // Đề xuất thì phải chọn giờ trước mới biết bác sĩ nào rảnh.
    enabled: mode === "assign" || when !== null,
  });

  const submit = useMutation({
    mutationFn: () =>
      mode === "assign"
        ? bookingApi.assign(appointment.id, doctorId)
        : bookingApi.propose(appointment.id, { doctorUserId: doctorId, date, startTime: time }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      qc.invalidateQueries({ queryKey: ["clinic-hours"] });
      toast.success(
        mode === "assign" ? "Đã xếp bác sĩ, lịch được xác nhận" : "Đã gửi đề xuất, chờ khách đồng ý",
      );
      onClose();
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : "Không xếp được lịch này.");
      // Bác sĩ có thể vừa bị người khác xếp mất: tải lại danh sách để thấy tình trạng thật.
      doctors.refetch();
      setDoctorId("");
    },
  });

  const list = doctors.data ?? [];
  const freeCount = list.filter((d) => d.free).length;
  const chosen = list.find((d) => d.doctorUserId === doctorId);
  const pet = detail.data?.pet ?? null;
  const askedPast =
    askedDate < todayISO(); // giờ khách chọn đã qua: không xếp được nữa, chỉ đề xuất hoặc huỷ.

  const ready = doctorId !== "" && chosen?.free === true && (mode === "assign" || time !== "");

  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title="Xếp bác sĩ cho lịch khám"
      description={`Khách chọn ${formatTime(askedTime)}, ${formatDate(askedDate)}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Đóng
          </Button>
          <Button disabled={!ready} loading={submit.isPending} onClick={() => submit.mutate()}>
            {mode === "assign" ? "Xếp bác sĩ này" : "Gửi đề xuất cho khách"}
          </Button>
        </>
      }
    >
      <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
        <Item label="Bé">
          {pet ? `${pet.name} · ${pet.species}${pet.breed ? ` ${pet.breed}` : ""}` : detail.isLoading ? "Đang tải…" : "Không rõ"}
        </Item>
        <Item label="Dịch vụ">{appointment.serviceName ?? "Chưa chọn"}</Item>
        <Item label="Lý do khám" wide>
          {appointment.reason?.trim() || "Không ghi"}
        </Item>
        {pet?.allergies && (
          <Item label="Dị ứng" wide>
            <span className="inline-flex items-start gap-1.5">
              <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0 text-amber" />
              {pet.allergies}
            </span>
          </Item>
        )}
      </dl>

      {canAssign && (
        <div className="mt-5 inline-flex rounded-[var(--radius-control)] border border-line-strong p-0.5" role="tablist">
          <ModeTab active={mode === "assign"} disabled={askedPast} onClick={() => setMode("assign")}>
            Xếp đúng giờ khách chọn
          </ModeTab>
          <ModeTab active={mode === "propose"} onClick={() => setMode("propose")}>
            Đề xuất giờ khác
          </ModeTab>
        </div>
      )}

      {askedPast && canAssign && (
        <p className="mt-3 text-sm text-danger">
          Giờ khách chọn đã qua nên không xếp được nữa. Hãy đề xuất giờ khác hoặc huỷ yêu cầu.
        </p>
      )}

      {mode === "propose" && (
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="propose-date" className="mb-1.5 block text-sm font-medium">
              Ngày đề xuất
            </label>
            <input
              id="propose-date"
              type="date"
              min={todayISO()}
              max={addDaysISO(todayISO(), HORIZON_DAYS - 1)}
              value={date}
              onChange={(e) => {
                setDate(e.target.value);
                setTime("");
                setDoctorId("");
              }}
              className={cn(controlClass, "h-9")}
            />
          </div>
          <SelectField
            label="Giờ đề xuất"
            value={time}
            onChange={(e) => {
              setTime(e.target.value);
              setDoctorId("");
            }}
            hint={hours.isError ? "Không tải được giờ nhận lịch." : "Giờ gạch chân là đã đủ lịch."}
          >
            <option value="">Chọn giờ…</option>
            {(hours.data ?? []).map((h) => {
              // Trùng giờ khách đã chọn thì không phải đề xuất mà là xếp thẳng.
              const sameAsAsked = date === askedDate && h.startTime === askedTime;
              return (
                <option key={h.startTime} value={h.startTime} disabled={!h.available || sameAsAsked}>
                  {formatTime(h.startTime)}
                  {sameAsAsked ? " (giờ khách chọn)" : !h.available ? " (đã đủ)" : ""}
                </option>
              );
            })}
          </SelectField>
        </div>
      )}

      <div className="mt-5">
        <h3 className="text-sm font-medium text-ink">Chọn bác sĩ</h3>

        {mode === "propose" && !time ? (
          <p className="mt-2 text-sm text-bark">Chọn giờ đề xuất để xem bác sĩ nào rảnh.</p>
        ) : doctors.isLoading ? (
          <p className="mt-2 text-sm text-bark">Đang tải danh sách bác sĩ…</p>
        ) : doctors.isError ? (
          <div className="mt-2">
            <ErrorState
              message={doctors.error instanceof ApiError ? doctors.error.message : "Không tải được danh sách bác sĩ."}
              onRetry={() => doctors.refetch()}
            />
          </div>
        ) : list.length === 0 ? (
          <p className="mt-2 text-sm text-bark">Chưa có bác sĩ nào đang làm việc.</p>
        ) : (
          <>
            {freeCount === 0 && (
              <p className="mt-2 text-sm text-bark">
                {mode === "assign"
                  ? "Giờ này không bác sĩ nào rảnh. Chuyển sang “Đề xuất giờ khác” để chọn giờ có bác sĩ trống."
                  : "Giờ này không bác sĩ nào rảnh. Chọn giờ khác."}
              </p>
            )}
            <ul className="mt-2 divide-y divide-line rounded-[var(--radius-control)] border border-line">
              {list.map((d) => (
                <DoctorRow
                  key={d.doctorUserId}
                  doctor={d}
                  selected={doctorId === d.doctorUserId}
                  onSelect={() => setDoctorId(d.doctorUserId)}
                />
              ))}
            </ul>
          </>
        )}
      </div>

      {mode === "propose" && (
        <p className="mt-4 text-sm text-bark">
          Đề xuất giữ chỗ bác sĩ cho khách ngay. Khách đồng ý thì lịch được xác nhận; khách huỷ thì chỗ
          được trả lại.
        </p>
      )}
    </Dialog>
  );
}

function Item({ label, wide, children }: { label: string; wide?: boolean; children: React.ReactNode }) {
  return (
    <div className={wide ? "sm:col-span-2" : undefined}>
      <dt className="text-bark">{label}</dt>
      <dd className="mt-0.5 text-ink">{children}</dd>
    </div>
  );
}

function ModeTab({
  active,
  disabled,
  onClick,
  children,
}: {
  active: boolean;
  disabled?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "rounded-[calc(var(--radius-control)-2px)] px-3 py-1.5 text-sm transition-colors",
        active ? "bg-moss text-white" : "text-ink-soft hover:bg-paper",
        disabled && "cursor-not-allowed opacity-50",
      )}
    >
      {children}
    </button>
  );
}

function DoctorRow({
  doctor: d,
  selected,
  onSelect,
}: {
  doctor: DoctorAvailability;
  selected: boolean;
  onSelect: () => void;
}) {
  const name = d.fullName?.trim() ? `BS. ${d.fullName.trim()}` : "Bác sĩ phòng khám";
  return (
    <li>
      <label
        className={cn(
          "flex items-center gap-3 px-3 py-2.5",
          d.free ? "cursor-pointer hover:bg-paper" : "cursor-not-allowed opacity-60",
          selected && "bg-moss-wash",
        )}
      >
        <input
          type="radio"
          name="doctor"
          checked={selected}
          disabled={!d.free}
          onChange={onSelect}
          className="size-4 accent-[var(--color-moss)]"
        />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-medium text-ink">{name}</span>
          {d.specialty && <span className="block text-xs text-bark">{d.specialty}</span>}
        </span>
        <span className="flex flex-wrap justify-end gap-1.5">
          {d.free ? <Tag>Rảnh giờ này</Tag> : <Tag className="border-danger/40 text-danger">Bận giờ này</Tag>}
          {d.onShift && <Tag>Có ca trực</Tag>}
          <Tag>{d.dayLoad} lịch trong ngày</Tag>
        </span>
      </label>
    </li>
  );
}
