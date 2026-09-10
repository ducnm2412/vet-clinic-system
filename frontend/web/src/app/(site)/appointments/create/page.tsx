"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Check, ChevronLeft, Plus } from "lucide-react";
import { ApiError, bookingApi, customerApi, suggestionsFrom } from "@/lib/api";
import { formatAge, formatDate, formatTime, todayISO } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { useToast } from "@/components/ui";
import { CLINIC } from "@/config/clinic";
import type { Pet, SuggestedSlot } from "@/types";
import { Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { PetFormDialog } from "@/components/site/PetFormDialog";
import { SiteTextarea } from "@/components/site/fields";

const STEPS = ["Chọn bé", "Chọn ngày", "Chọn giờ", "Xác nhận"] as const;

export default function CreateAppointmentPage() {
  return (
    <CustomerOnly>
      <BookingFlow />
    </CustomerOnly>
  );
}

function BookingFlow() {
  const router = useRouter();
  const toast = useToast();

  const [step, setStep] = useState(0);
  const [petId, setPetId] = useState("");
  const [date, setDate] = useState(todayISO());
  const [startTime, setStartTime] = useState("");
  const [reason, setReason] = useState("");
  const [suggestions, setSuggestions] = useState<SuggestedSlot[]>([]);
  const [addingPet, setAddingPet] = useState(false);

  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: customerApi.pets });

  const times = useQuery({
    queryKey: ["available-times", date],
    queryFn: () => bookingApi.availableTimes(date),
    enabled: date !== "",
  });

  const book = useMutation({
    mutationFn: () => bookingApi.create({ petId, date, startTime, reason: reason || undefined }),
    onSuccess: (appointment) => {
      toast.success("Đã đặt lịch khám");
      router.push(`/appointments/${appointment.id}`);
    },
    onError: (err) => {
      // Khung giờ vừa bị người khác đặt mất: backend trả 409 kèm tối đa 3 gợi ý thay thế.
      const alt = suggestionsFrom(err);
      setSuggestions(alt);
      if (alt.length > 0) {
        setStep(2);
        setStartTime("");
      } else {
        toast.error(err instanceof ApiError ? err.message : "Không đặt được lịch.");
      }
    },
  });

  const list = pets.data ?? [];
  const pet = list.find((p) => p.id === petId) ?? null;

  if (pets.isLoading) {
    return (
      <Container className="py-20">
        <div className="h-64 animate-pulse rounded-[var(--radius-card)] bg-mint" />
      </Container>
    );
  }

  if (list.length === 0) {
    return (
      <>
        <Container className="py-20 text-center md:py-28">
          <PetAvatar species={null} className="mx-auto h-48 w-36" />
          <h1 className="t-h2 mt-10">Thêm bé nhà bạn trước đã</h1>
          <p className="measure mx-auto mt-4 text-stone">
            Bác sĩ cần biết khám cho bé nào. Chỉ cần tên và loài là đặt lịch được, phần còn
            lại điền sau cũng không sao.
          </p>
          <SiteButton size="lg" className="mt-8" onClick={() => setAddingPet(true)}>
            <Plus aria-hidden className="size-4" />
            Thêm thú cưng
          </SiteButton>
        </Container>
        {addingPet && <PetFormDialog open pet={null} onClose={() => setAddingPet(false)} />}
      </>
    );
  }

  const canContinue =
    step === 0 ? petId !== "" : step === 1 ? date !== "" : step === 2 ? startTime !== "" : true;

  return (
    <>
      <div className="bg-mint py-12 md:py-16">
        <Container>
          <h1 className="t-h2">Đặt lịch khám</h1>
          <p className="measure t-lede mt-4 text-stone">
            Bốn bước. Bạn không phải chọn bác sĩ — phòng khám xếp người đang rảnh vào khung
            giờ bạn chọn.
          </p>
        </Container>
      </div>

      <Container className="py-10 md:py-14">
        <Stepper current={step} onJump={(i) => i < step && setStep(i)} />

        <div className="mt-10 grid gap-12 lg:grid-cols-[1fr_20rem] lg:gap-16">
          <div className="min-w-0">
            {step === 0 && (
              <StepPets
                pets={list}
                petId={petId}
                onPick={(id) => {
                  setPetId(id);
                  setStep(1);
                }}
                onAdd={() => setAddingPet(true)}
              />
            )}

            {step === 1 && (
              <StepDate
                date={date}
                onChange={(d) => {
                  setDate(d);
                  setStartTime("");
                  setSuggestions([]);
                }}
              />
            )}

            {step === 2 && (
              <StepTime
                date={date}
                times={times.data ?? []}
                loading={times.isLoading}
                failed={times.isError}
                onRetry={() => times.refetch()}
                startTime={startTime}
                suggestions={suggestions}
                onPick={(t) => {
                  setStartTime(t);
                  setSuggestions([]);
                }}
                onPickSuggestion={(s) => {
                  setDate(s.date);
                  setStartTime(s.startTime);
                  setSuggestions([]);
                }}
                onBackToDate={() => setStep(1)}
              />
            )}

            {step === 3 && (
              <StepConfirm
                pet={pet}
                date={date}
                startTime={startTime}
                reason={reason}
                onReason={setReason}
              />
            )}

            <div className="mt-10 flex flex-wrap items-center gap-3">
              {step > 0 && (
                <SiteButton variant="outline" onClick={() => setStep(step - 1)}>
                  <ChevronLeft aria-hidden className="size-4" />
                  Quay lại
                </SiteButton>
              )}

              {step < 3 ? (
                <SiteButton disabled={!canContinue} onClick={() => setStep(step + 1)}>
                  Tiếp tục
                </SiteButton>
              ) : (
                <SiteButton size="lg" loading={book.isPending} onClick={() => book.mutate()}>
                  Đặt lịch khám
                </SiteButton>
              )}
            </div>
          </div>

          <BookingSummary pet={pet} date={date} startTime={startTime} step={step} />
        </div>
      </Container>

      {addingPet && <PetFormDialog open pet={null} onClose={() => setAddingPet(false)} />}
    </>
  );
}

/** Thanh bước. Bấm quay lại bước đã qua được, không nhảy tới bước chưa tới. */
function Stepper({ current, onJump }: { current: number; onJump: (i: number) => void }) {
  return (
    <ol className="flex flex-wrap gap-x-2 gap-y-3">
      {STEPS.map((label, i) => {
        const done = i < current;
        const active = i === current;
        return (
          <li key={label} className="flex items-center gap-2">
            <button
              onClick={() => onJump(i)}
              disabled={!done}
              aria-current={active ? "step" : undefined}
              className={cn(
                "flex items-center gap-2 rounded-full py-2 pl-2 pr-4 text-[15px] transition-colors",
                active && "bg-pine text-white",
                done && "text-pine hover:bg-mint",
                !active && !done && "text-stone",
              )}
            >
              <span
                aria-hidden
                className={cn(
                  "grid size-7 place-items-center rounded-full text-[14px] font-semibold",
                  active ? "bg-white text-pine" : done ? "bg-teal text-white" : "bg-mist text-stone",
                )}
              >
                {done ? <Check className="size-4" /> : i + 1}
              </span>
              {label}
            </button>
            {i < STEPS.length - 1 && (
              <span aria-hidden className="hidden h-px w-6 bg-mist sm:block" />
            )}
          </li>
        );
      })}
    </ol>
  );
}

function StepPets({
  pets,
  petId,
  onPick,
  onAdd,
}: {
  pets: Pet[];
  petId: string;
  onPick: (id: string) => void;
  onAdd: () => void;
}) {
  return (
    <section>
      <h2 className="t-h2">Khám cho bé nào?</h2>
      <p className="measure mt-3 text-stone">
        Chọn một bé. Muốn khám hai bé cùng hôm thì đặt hai lịch riêng để mỗi bé có đủ thời gian.
      </p>

      <ul className="mt-8 grid gap-4 sm:grid-cols-2">
        {pets.map((pet) => (
          <li key={pet.id}>
            <button
              onClick={() => onPick(pet.id)}
              aria-pressed={petId === pet.id}
              className={cn(
                "flex w-full items-center gap-4 rounded-[var(--radius-card)] border p-4 text-left transition-colors",
                petId === pet.id
                  ? "border-teal bg-mint"
                  : "border-mist hover:border-teal hover:bg-mint/60",
              )}
            >
              <PetAvatar species={pet.species} small className="h-16 w-13" />
              <span className="min-w-0">
                <span className="block font-[family-name:var(--font-brand)] text-[19px] font-semibold">
                  {pet.name}
                </span>
                <span className="block text-stone">
                  {pet.species}
                  {pet.breed ? ` ${pet.breed}` : ""}
                </span>
                <span className="block text-[15px] text-stone">{formatAge(pet.dateOfBirth)}</span>
              </span>
            </button>
          </li>
        ))}

        <li>
          <button
            onClick={onAdd}
            className="flex h-full min-h-28 w-full items-center justify-center gap-2 rounded-[var(--radius-card)] border border-dashed border-mist p-4 text-stone transition-colors hover:border-teal hover:text-pine"
          >
            <Plus aria-hidden className="size-5" />
            Thêm bé khác
          </button>
        </li>
      </ul>
    </section>
  );
}

function StepDate({ date, onChange }: { date: string; onChange: (d: string) => void }) {
  const today = todayISO();

  // Bảy ngày tới bấm được ngay; xa hơn thì dùng ô chọn ngày.
  const quick = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(`${today}T00:00:00`);
    d.setDate(d.getDate() + i);
    const p = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  });

  const weekday = ["Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"];

  return (
    <section>
      <h2 className="t-h2">Ngày nào bạn tiện?</h2>
      <p className="measure mt-3 text-stone">
        Phòng khám mở cả tuần, {CLINIC.hours.toLowerCase().replace("thứ hai đến chủ nhật, ", "")}.
      </p>

      <ul className="mt-8 flex flex-wrap gap-3">
        {quick.map((d, i) => {
          const day = new Date(`${d}T00:00:00`);
          return (
            <li key={d}>
              <button
                onClick={() => onChange(d)}
                aria-pressed={date === d}
                className={cn(
                  "w-24 rounded-[var(--radius-card)] border px-3 py-3 text-center transition-colors",
                  date === d
                    ? "border-teal bg-mint"
                    : "border-mist hover:border-teal hover:bg-mint/60",
                )}
              >
                <span className="block text-[14px] text-stone">
                  {i === 0 ? "Hôm nay" : weekday[day.getDay()]}
                </span>
                <span className="tnum block text-[19px] font-semibold">
                  {String(day.getDate()).padStart(2, "0")}/{String(day.getMonth() + 1).padStart(2, "0")}
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      <label className="mt-8 block max-w-60">
        <span className="mb-1.5 block text-[15px] font-medium">Hoặc chọn ngày khác</span>
        <input
          type="date"
          min={today}
          value={date}
          onChange={(e) => onChange(e.target.value)}
          className="h-12 w-full rounded-xl border border-mist bg-white px-4 text-[16px] outline-none focus:border-teal"
        />
      </label>
    </section>
  );
}

function StepTime({
  date,
  times,
  loading,
  failed,
  onRetry,
  startTime,
  suggestions,
  onPick,
  onPickSuggestion,
  onBackToDate,
}: {
  date: string;
  times: Array<{ startTime: string; endTime: string; availableCount: number }>;
  loading: boolean;
  failed: boolean;
  onRetry: () => void;
  startTime: string;
  suggestions: SuggestedSlot[];
  onPick: (t: string) => void;
  onPickSuggestion: (s: SuggestedSlot) => void;
  onBackToDate: () => void;
}) {
  return (
    <section>
      <h2 className="t-h2">Mấy giờ thì được?</h2>
      <p className="measure mt-3 text-stone">
        Đây là khung giờ thật sự còn chỗ ngày {formatDate(date)}. Mỗi ca kéo dài 30 phút.
      </p>

      {suggestions.length > 0 && (
        <div className="mt-6 rounded-[var(--radius-card)] bg-peach p-5">
          <p className="font-medium text-coral-deep">
            Khung giờ bạn chọn vừa có người đặt mất.
          </p>
          <p className="mt-1 text-stone">Vài giờ khác còn trống:</p>
          <ul className="mt-4 flex flex-wrap gap-2">
            {suggestions.map((s, i) => (
              <li key={i}>
                <button
                  onClick={() => onPickSuggestion(s)}
                  className="rounded-full border border-mist bg-white px-4 py-2.5 text-[15px] transition-colors hover:border-teal"
                >
                  <span className="tnum font-medium">{formatTime(s.startTime)}</span>
                  <span className="ml-2 text-stone">{formatDate(s.date)}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {loading ? (
        <ul className="mt-8 flex flex-wrap gap-3" aria-hidden>
          {Array.from({ length: 8 }).map((_, i) => (
            <li key={i} className="h-14 w-28 animate-pulse rounded-full bg-mint" />
          ))}
        </ul>
      ) : failed ? (
        <div className="mt-8 rounded-[var(--radius-card)] border border-mist px-6 py-10 text-center">
          <p className="t-h3">Chưa xem được giờ trống</p>
          <p className="mt-2 text-stone">Kết nối tới phòng khám đang trục trặc.</p>
          <SiteButton variant="outline" className="mt-6" onClick={onRetry}>
            Thử lại
          </SiteButton>
        </div>
      ) : times.length === 0 ? (
        <div className="mt-8 rounded-[var(--radius-card)] bg-mint px-6 py-12 text-center">
          <p className="t-h3">Ngày {formatDate(date)} đã kín chỗ</p>
          <p className="measure mx-auto mt-2 text-stone">
            Không còn khung giờ nào trống. Chọn ngày khác giúp bạn nhé, hoặc gọi{" "}
            {CLINIC.phone} để phòng khám xếp thêm.
          </p>
          <SiteButton variant="outline" className="mt-6" onClick={onBackToDate}>
            Chọn ngày khác
          </SiteButton>
        </div>
      ) : (
        <ul className="mt-8 flex flex-wrap gap-3">
          {times.map((t) => (
            <li key={t.startTime}>
              {/* Nút cao 56px: trên điện thoại bấm bằng ngón cái không trượt sang ô bên. */}
              <button
                onClick={() => onPick(t.startTime)}
                aria-pressed={startTime === t.startTime}
                className={cn(
                  "h-14 min-w-28 rounded-full border px-5 transition-colors",
                  startTime === t.startTime
                    ? "border-teal bg-teal text-white"
                    : "border-mist hover:border-teal hover:bg-mint",
                )}
              >
                <span className="tnum text-[17px] font-medium">{formatTime(t.startTime)}</span>
                {t.availableCount > 1 && (
                  <span
                    className={cn(
                      "tnum ml-2 text-[14px]",
                      startTime === t.startTime ? "text-white/75" : "text-stone",
                    )}
                  >
                    còn {t.availableCount}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function StepConfirm({
  pet,
  date,
  startTime,
  reason,
  onReason,
}: {
  pet: Pet | null;
  date: string;
  startTime: string;
  reason: string;
  onReason: (v: string) => void;
}) {
  return (
    <section>
      <h2 className="t-h2">Bé đang bị gì?</h2>
      <p className="measure mt-3 text-stone">
        Ghi vài dòng thôi cũng được. Bác sĩ đọc trước nên lúc bạn đến là vào việc luôn.
      </p>

      <div className="mt-8 max-w-xl">
        <SiteTextarea
          label="Lý do khám"
          rows={4}
          value={reason}
          onChange={(e) => onReason(e.target.value)}
          placeholder="Ví dụ: bỏ ăn hai ngày, hay gãi tai, đi ngoài lỏng…"
          hint="Không bắt buộc, nhưng ghi rõ thì bác sĩ chuẩn bị tốt hơn."
        />
      </div>

      {/*
        TODO(backend, VD-21): AppointmentRequest không có trường dịch vụ, nên khách không
        chọn được "tiêm phòng" hay "phẫu thuật" thành một mục riêng. Tạm hướng dẫn viết vào
        ô lý do. Cần bổ sung: serviceId trong AppointmentRequest và một danh mục dịch vụ.
      */}
      <p className="measure mt-4 text-[15px] text-stone">
        Cần tiêm phòng hay làm thủ thuật? Ghi luôn vào ô trên, phòng khám sẽ chuẩn bị trước.
      </p>

      <dl className="mt-10 max-w-xl divide-y divide-mist rounded-[var(--radius-card)] border border-mist px-6">
        <Line label="Bé" value={pet?.name ?? "Chưa chọn"} />
        <Line label="Ngày" value={formatDate(date)} />
        <Line label="Giờ" value={formatTime(startTime)} />
        <Line label="Tại" value={CLINIC.address} />
      </dl>
    </section>
  );
}

function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-wrap justify-between gap-4 py-4">
      <dt className="text-stone">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  );
}

/** Tóm tắt bám bên phải để người ta luôn thấy mình đã chọn gì, khỏi phải lùi lại xem. */
function BookingSummary({
  pet,
  date,
  startTime,
  step,
}: {
  pet: Pet | null;
  date: string;
  startTime: string;
  step: number;
}) {
  if (step === 0) return null;

  return (
    <aside className="lg:sticky lg:top-24 lg:self-start">
      <div className="rounded-[var(--radius-card)] bg-mint p-6">
        <h2 className="t-h3">Lịch của bạn</h2>
        <dl className="mt-5 space-y-4 text-[15px]">
          {pet && (
            <div className="flex items-center gap-4">
              <PetAvatar species={pet.species} small className="h-14 w-11" />
              <div>
                <dt className="text-stone">Khám cho</dt>
                <dd className="font-medium">{pet.name}</dd>
              </div>
            </div>
          )}
          <div>
            <dt className="text-stone">Ngày</dt>
            <dd className="font-medium">{formatDate(date)}</dd>
          </div>
          <div>
            <dt className="text-stone">Giờ</dt>
            <dd className="tnum font-medium">
              {startTime ? formatTime(startTime) : "Chưa chọn"}
            </dd>
          </div>
        </dl>

        <p className="mt-6 border-t border-mist pt-5 text-[15px] text-stone">
          Đặt xong bạn vẫn huỷ được, miễn là trước giờ khám.
        </p>
      </div>
    </aside>
  );
}
