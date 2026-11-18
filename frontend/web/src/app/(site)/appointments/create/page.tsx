"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Clock3,
  Info,
  MapPin,
  PawPrint,
  Plus,
  Send,
  Stethoscope,
} from "lucide-react";
import { ApiError, bookingApi, clinicServiceApi, myPetApi } from "@/lib/api";
import { formatAge, formatDate, formatTime, todayISO } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { useToast } from "@/components/ui";
import { CLINIC } from "@/config/clinic";
import type { ClinicHour, ClinicService, Pet } from "@/types";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { PetFormDialog } from "@/components/site/PetFormDialog";
import { btnOutline, btnPrimary, card, field, fieldLabel } from "@/components/user/ui";

const STEPS = ["Chọn bé", "Chọn ngày", "Chọn giờ", "Xác nhận"] as const;

/** Phòng khám nhận yêu cầu trước tối đa bấy nhiêu ngày (khớp REQUEST_HORIZON_DAYS ở backend). */
const BOOKING_HORIZON_DAYS = 30;

const WEEKDAY = ["CN", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];

/** Cộng thêm `n` ngày vào một chuỗi ngày dạng "yyyy-MM-dd". */
function addDaysISO(iso: string, n: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + n);
  const p = (v: number) => String(v).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

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
  const [serviceId, setServiceId] = useState("");
  const [addingPet, setAddingPet] = useState(false);

  const qc = useQueryClient();
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: myPetApi.list });

  // VD-21: danh mục dịch vụ lấy từ backend. Gọi hỏng thì ẩn ô chọn, khách vẫn đặt được.
  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });

  // Đi từ trang dịch vụ (?service=<slug>) thì chọn sẵn dịch vụ đó — chỉ áp một lần.
  const preset = useRef(false);
  useEffect(() => {
    if (preset.current || !services.data) return;
    preset.current = true;
    const slug = new URLSearchParams(window.location.search).get("service");
    const hit = slug ? services.data.find((s) => s.slug === slug) : undefined;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (hit) setServiceId(hit.id);
  }, [services.data]);

  // Mốc giờ theo giờ làm việc của phòng khám, không phụ thuộc bác sĩ đã có ca hay chưa: khách chỉ
  // gửi yêu cầu, phòng khám xếp bác sĩ sau.
  const times = useQuery({
    queryKey: ["clinic-hours", date],
    queryFn: () => bookingApi.clinicHours(date),
    enabled: date !== "",
  });

  // Kiểm tra riêng cho "hôm nay": quá giờ khám cuối ngày (hoặc mọi mốc còn lại đã đầy) thì không
  // cho chọn "Hôm nay" ở bước 2. Còn đang tải hoặc lỗi thì coi như vẫn chọn được.
  const todayDate = todayISO();
  const todayTimes = useQuery({
    queryKey: ["clinic-hours", todayDate],
    queryFn: () => bookingApi.clinicHours(todayDate),
  });
  const todayBookable = todayTimes.data === undefined || todayTimes.data.some((t) => t.available);

  useEffect(() => {
    if (!todayBookable && date === todayDate) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDate(addDaysISO(todayDate, 1));
      setStartTime("");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [todayBookable]);

  const book = useMutation({
    mutationFn: () =>
      bookingApi.request({
        petId,
        date,
        startTime,
        serviceId: serviceId || undefined,
        reason: reason || undefined,
      }),
    onSuccess: (appointment) => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      qc.invalidateQueries({ queryKey: ["clinic-hours"] });
      toast.success("Đã gửi yêu cầu đặt lịch");
      router.push(`/appointments/${appointment.id}`);
    },
    onError: async (err) => {
      toast.error(err instanceof ApiError ? err.message : "Không gửi được yêu cầu đặt lịch.");
      // Có thể giờ vừa chọn bị người khác lấy mất chỗ cuối: tải lại, nếu mốc đó đã đầy thì đưa
      // khách về bước chọn giờ. Lỗi khác thì ở yên bước này.
      const fresh = await times.refetch();
      const chosen = fresh.data?.find((t) => t.startTime === startTime);
      if (!chosen?.available) {
        setStartTime("");
        setStep(2);
      }
    },
  });

  const list = pets.data ?? [];
  const pet = list.find((p) => p.id === petId) ?? null;
  const serviceList = services.data ?? [];
  const service = serviceList.find((s) => s.id === serviceId) ?? null;

  if (pets.isLoading) {
    return (
      <Shell>
        <div className="h-72 animate-pulse rounded-[20px] bg-white" />
      </Shell>
    );
  }

  if (list.length === 0) {
    return (
      <Shell>
        <div className={cn(card, "px-6 py-12 text-center")}>
          <PetAvatar species={null} className="mx-auto h-40 w-30" />
          <h2 className="mt-6 text-[22px] font-extrabold text-[#1f4a3c]">Thêm bé nhà bạn trước đã</h2>
          <p className="mx-auto mt-2 max-w-md text-[15px] text-[#777777]">
            Bác sĩ cần biết khám cho bé nào. Chỉ cần tên và loài là đặt lịch được, phần còn lại điền
            sau cũng không sao.
          </p>
          <button type="button" className={cn(btnPrimary, "mt-6")} onClick={() => setAddingPet(true)}>
            <Plus aria-hidden className="size-4" />
            Thêm thú cưng
          </button>
        </div>
        {addingPet && <PetFormDialog open pet={null} onClose={() => setAddingPet(false)} />}
      </Shell>
    );
  }

  const canContinue =
    step === 0 ? petId !== "" : step === 1 ? date !== "" : step === 2 ? startTime !== "" : true;

  return (
    <Shell>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-6">
        <div className={cn(card, "min-w-0 p-4 sm:p-6")}>
          <Stepper current={step} onJump={(i) => i < step && setStep(i)} />

          <div className="mt-6 border-t border-[#f3ece4] pt-6">
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
                todayBookable={todayBookable}
                onChange={(d) => {
                  setDate(d);
                  setStartTime("");
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
                onPick={setStartTime}
                onBackToDate={() => setStep(1)}
              />
            )}

            {step === 3 && (
              <StepConfirm
                services={serviceList}
                reason={reason}
                onReason={setReason}
                serviceId={serviceId}
                onServiceId={setServiceId}
              />
            )}
          </div>

          <div className="mt-8 flex items-center justify-between gap-3 border-t border-[#f3ece4] pt-5">
            {step > 0 ? (
              <button type="button" className={btnOutline} onClick={() => setStep(step - 1)}>
                <ChevronLeft aria-hidden className="size-4" />
                Quay lại
              </button>
            ) : (
              <span />
            )}

            {step < 3 ? (
              <button
                type="button"
                className={btnPrimary}
                disabled={!canContinue}
                onClick={() => setStep(step + 1)}
              >
                Tiếp tục
                <ChevronRight aria-hidden className="size-4" />
              </button>
            ) : (
              <button
                type="button"
                className={btnPrimary}
                disabled={book.isPending}
                onClick={() => book.mutate()}
              >
                <Send aria-hidden className="size-4" />
                {book.isPending ? "Đang gửi…" : "Gửi yêu cầu đặt lịch"}
              </button>
            )}
          </div>
        </div>

        <BookingSummary pet={pet} date={date} startTime={startTime} step={step} service={service} />
      </div>

      {addingPet && <PetFormDialog open pet={null} onClose={() => setAddingPet(false)} />}
    </Shell>
  );
}

/** Khung trang: nền kem + breadcrumb + tiêu đề, giống khu tài khoản. */
function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-[#fffcf8]">
      <div className="mx-auto w-full max-w-[1200px] px-4 py-6 sm:px-6 lg:py-10">
        <header className="mb-6">
          <nav aria-label="Breadcrumb" className="mb-2 flex items-center gap-1 text-[13px] text-[#777777]">
            <Link href="/" className="hover:text-[#f57c1f]">
              Trang chủ
            </Link>
            <ChevronRight aria-hidden className="size-3.5" />
            <Link href="/user/appointments" className="hover:text-[#f57c1f]">
              Lịch khám
            </Link>
            <ChevronRight aria-hidden className="size-3.5" />
            <span aria-current="page" className="text-[#242424]">
              Đặt lịch
            </span>
          </nav>
          <h1 className="text-[26px] font-extrabold leading-tight text-[#1f4a3c] sm:text-[28px]">
            Đặt lịch khám
          </h1>
        </header>
        {children}
      </div>
    </div>
  );
}

/** Thanh bước. Bấm quay lại bước đã qua được, không nhảy tới bước chưa tới. */
function Stepper({ current, onJump }: { current: number; onJump: (i: number) => void }) {
  return (
    <ol className="grid grid-cols-4 gap-1.5 sm:gap-3">
      {STEPS.map((label, i) => {
        const done = i < current;
        const active = i === current;
        return (
          <li key={label}>
            <button
              type="button"
              onClick={() => onJump(i)}
              disabled={!done}
              aria-current={active ? "step" : undefined}
              className="group flex w-full flex-col items-start gap-2 text-left"
            >
              <span
                aria-hidden
                className={cn(
                  "h-1.5 w-full rounded-full transition-colors",
                  active || done ? "bg-[#f57c1f]" : "bg-[#f1e9e1]",
                )}
              />
              <span className="flex items-center gap-1.5">
                <span
                  aria-hidden
                  className={cn(
                    "grid size-6 shrink-0 place-items-center rounded-full text-[12px] font-bold",
                    active
                      ? "bg-[#f57c1f] text-white"
                      : done
                        ? "bg-[#e3f2ec] text-[#1f4a3c]"
                        : "bg-[#f4f1ee] text-[#999999]",
                  )}
                >
                  {done ? <Check className="size-3.5" /> : i + 1}
                </span>
                <span
                  className={cn(
                    "hidden text-[14px] sm:inline",
                    active ? "font-semibold text-[#242424]" : done ? "text-[#1f4a3c] group-hover:text-[#f57c1f]" : "text-[#999999]",
                  )}
                >
                  {label}
                </span>
              </span>
            </button>
          </li>
        );
      })}
    </ol>
  );
}

function StepTitle({ icon: Icon, title, hint }: { icon: typeof PawPrint; title: string; hint: React.ReactNode }) {
  return (
    <div className="mb-5 flex items-start gap-3">
      <span className="grid size-10 shrink-0 place-items-center rounded-full bg-[#fff1e3] text-[#f57c1f]">
        <Icon aria-hidden className="size-5" />
      </span>
      <div className="min-w-0">
        <h2 className="text-[19px] font-bold leading-tight text-[#1f4a3c] sm:text-[21px]">{title}</h2>
        <p className="mt-1 text-[14px] text-[#777777]">{hint}</p>
      </div>
    </div>
  );
}

// Nền KHÔNG đặt ở pickBase: cn() chỉ nối chuỗi, bg-white ở đây sẽ đè nền cam của ô đang chọn (chữ trắng trên nền trắng).
const pickBase = "rounded-2xl border transition duration-200";
const pickIdle = "border-[#eeeeee] bg-white hover:-translate-y-0.5 hover:border-[#f9cfa8] hover:shadow-[0_12px_24px_-18px_rgba(245,124,31,0.6)]";
const pickOn = "border-[#f57c1f] bg-[#fff8f1] ring-2 ring-[#f57c1f]/15";

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
      <StepTitle
        icon={PawPrint}
        title="Khám cho bé nào?"
        hint="Chọn một bé. Muốn khám hai bé cùng hôm thì đặt hai lịch riêng."
      />

      <ul className="grid gap-3 sm:grid-cols-2">
        {pets.map((pet) => {
          const on = petId === pet.id;
          return (
            <li key={pet.id}>
              <button
                type="button"
                onClick={() => onPick(pet.id)}
                aria-pressed={on}
                className={cn(pickBase, on ? pickOn : pickIdle, "relative flex w-full items-center gap-4 p-3.5 text-left")}
              >
                <PetAvatar species={pet.species} photoUrl={pet.photoUrl} name={pet.name} small className="h-16 w-13 shrink-0" />
                <span className="min-w-0">
                  <span className="block truncate text-[17px] font-bold text-[#1f4a3c]">{pet.name}</span>
                  <span className="block truncate text-[13px] text-[#777777]">
                    {pet.species}
                    {pet.breed ? ` · ${pet.breed}` : ""}
                  </span>
                  <span className="block text-[13px] text-[#777777]">{formatAge(pet.dateOfBirth)}</span>
                </span>
                {on && (
                  <span className="absolute right-3 top-3 grid size-5 place-items-center rounded-full bg-[#f57c1f] text-white">
                    <Check aria-hidden className="size-3.5" />
                  </span>
                )}
              </button>
            </li>
          );
        })}

        <li>
          <button
            type="button"
            onClick={onAdd}
            className="flex h-full min-h-[92px] w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-[#f2c9a3] bg-[#fffaf5] p-4 text-[15px] font-semibold text-[#f57c1f] transition-colors hover:border-[#f57c1f] hover:bg-[#fff1e3]"
          >
            <Plus aria-hidden className="size-5" />
            Thêm bé khác
          </button>
        </li>
      </ul>
    </section>
  );
}

function StepDate({
  date,
  todayBookable,
  onChange,
}: {
  date: string;
  todayBookable: boolean;
  onChange: (d: string) => void;
}) {
  const today = todayISO();
  const minDate = todayBookable ? today : addDaysISO(today, 1);
  const maxDate = addDaysISO(today, BOOKING_HORIZON_DAYS - 1);
  // Bảy ngày tới bấm được ngay; xa hơn thì dùng ô chọn ngày.
  const quick = Array.from({ length: 7 }, (_, i) => addDaysISO(today, i));

  return (
    <section>
      <StepTitle
        icon={CalendarDays}
        title="Ngày nào bạn tiện?"
        hint={<>Phòng khám mở cả tuần, {CLINIC.hours.toLowerCase().replace("thứ hai đến chủ nhật, ", "")}.</>}
      />

      <ul className="grid grid-cols-4 gap-2 sm:grid-cols-7 sm:gap-3">
        {quick.map((d, i) => {
          const day = new Date(`${d}T00:00:00`);
          const disabled = i === 0 && !todayBookable;
          const on = date === d;
          return (
            <li key={d}>
              <button
                type="button"
                onClick={() => !disabled && onChange(d)}
                disabled={disabled}
                aria-pressed={on}
                title={disabled ? "Hôm nay đã hết giờ nhận yêu cầu, chọn ngày khác giúp bạn nhé." : undefined}
                className={cn(
                  "w-full px-1 py-2.5 text-center",
                  pickBase,
                  disabled ? "cursor-not-allowed border-[#f1f1f1] bg-[#fafafa] text-[#bbbbbb]" : on ? "border-[#f57c1f] bg-[#f57c1f] text-white" : pickIdle,
                )}
              >
                <span className={cn("block text-[12px]", on ? "text-white/90" : "text-[#777777]")}>
                  {i === 0 ? "Hôm nay" : WEEKDAY[day.getDay()]}
                </span>
                <span className="tnum block text-[18px] font-bold">{String(day.getDate()).padStart(2, "0")}</span>
                <span className={cn("block text-[11px]", on ? "text-white/80" : "text-[#999999]")}>
                  {disabled ? "Hết giờ" : `Th ${day.getMonth() + 1}`}
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      {!todayBookable && (
        <p className="mt-3 text-[13px] text-[#777777]">Hôm nay phòng khám đã hết giờ nhận yêu cầu, chọn ngày mai trở đi giúp bạn nhé.</p>
      )}

      <label className="mt-5 block max-w-64">
        <span className={fieldLabel}>Hoặc chọn ngày khác</span>
        <input type="date" min={minDate} max={maxDate} value={date} onChange={(e) => onChange(e.target.value)} className={field} />
      </label>
    </section>
  );
}

/** Buổi sáng khám đến 11:30, buổi chiều từ 13:00 — chia hai nhóm cho dễ nhìn. */
const AFTERNOON_FROM = "12:00";

function StepTime({
  date,
  times,
  loading,
  failed,
  onRetry,
  startTime,
  onPick,
  onBackToDate,
}: {
  date: string;
  times: ClinicHour[];
  loading: boolean;
  failed: boolean;
  onRetry: () => void;
  startTime: string;
  onPick: (t: string) => void;
  onBackToDate: () => void;
}) {
  const morning = times.filter((t) => t.startTime < AFTERNOON_FROM);
  const afternoon = times.filter((t) => t.startTime >= AFTERNOON_FROM);
  const anyAvailable = times.some((t) => t.available);

  return (
    <section>
      <StepTitle
        icon={Clock3}
        title="Mấy giờ thì được?"
        hint={<>Giờ bạn muốn đến ngày {formatDate(date)}, mỗi ca 30 phút. Phòng khám xếp bác sĩ rồi báo lại qua email.</>}
      />

      {loading ? (
        <ul className="grid grid-cols-3 gap-2 sm:grid-cols-5" aria-hidden>
          {Array.from({ length: 10 }).map((_, i) => (
            <li key={i} className="h-11 animate-pulse rounded-xl bg-[#f6efe8]" />
          ))}
        </ul>
      ) : failed ? (
        <Empty title="Chưa xem được giờ nhận lịch" text="Kết nối tới phòng khám đang trục trặc.">
          <button type="button" className={btnOutline} onClick={onRetry}>
            Thử lại
          </button>
        </Empty>
      ) : !anyAvailable ? (
        <Empty
          title={`Ngày ${formatDate(date)} đã hết giờ nhận lịch`}
          text={`${times.length === 0 ? "Phòng khám chưa nhận lịch cho ngày này." : "Các giờ trong ngày đã đủ lịch hoặc đã qua."} Chọn ngày khác giúp bạn nhé, hoặc gọi ${CLINIC.phone}.`}
        >
          <button type="button" className={btnOutline} onClick={onBackToDate}>
            Chọn ngày khác
          </button>
        </Empty>
      ) : (
        <div className="space-y-5">
          <TimeGroup title="Buổi sáng" times={morning} startTime={startTime} onPick={onPick} />
          <TimeGroup title="Buổi chiều" times={afternoon} startTime={startTime} onPick={onPick} />
        </div>
      )}
    </section>
  );
}

function Empty({ title, text, children }: { title: string; text: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl bg-[#fff8f1] px-5 py-8 text-center">
      <p className="text-[17px] font-bold text-[#1f4a3c]">{title}</p>
      <p className="mx-auto mt-1.5 max-w-md text-[14px] text-[#777777]">{text}</p>
      <div className="mt-4">{children}</div>
    </div>
  );
}

function TimeGroup({
  title,
  times,
  startTime,
  onPick,
}: {
  title: string;
  times: ClinicHour[];
  startTime: string;
  onPick: (t: string) => void;
}) {
  if (times.length === 0) return null;
  return (
    <div>
      <h3 className="mb-2.5 text-[13px] font-semibold uppercase tracking-wide text-[#999999]">{title}</h3>
      <ul className="grid grid-cols-3 gap-2 sm:grid-cols-5">
        {times.map((t) => {
          const on = startTime === t.startTime;
          return (
            <li key={t.startTime}>
              <button
                type="button"
                onClick={() => onPick(t.startTime)}
                disabled={!t.available}
                aria-pressed={on}
                title={t.available ? undefined : "Giờ này đã đủ lịch hoặc đã qua"}
                className={cn(
                  "h-11 w-full rounded-xl border text-[15px] font-semibold transition-colors",
                  !t.available
                    ? "cursor-not-allowed border-[#f1f1f1] bg-[#fafafa] text-[#c4c4c4] line-through"
                    : on
                      ? "border-[#f57c1f] bg-[#f57c1f] text-white"
                      : "border-[#eeeeee] bg-white text-[#242424] hover:border-[#f57c1f] hover:text-[#f57c1f]",
                )}
              >
                <span className="tnum">{formatTime(t.startTime)}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function StepConfirm({
  services,
  reason,
  onReason,
  serviceId,
  onServiceId,
}: {
  services: ClinicService[];
  reason: string;
  onReason: (v: string) => void;
  serviceId: string;
  onServiceId: (v: string) => void;
}) {
  return (
    <section>
      <StepTitle
        icon={ClipboardList}
        title="Bé đang bị gì?"
        hint="Ghi vài dòng thôi cũng được. Bác sĩ đọc trước nên lúc bạn đến là vào việc luôn."
      />

      <div className="grid gap-4">
        {services.length > 0 && (
          <label className="block">
            <span className={fieldLabel}>Cần dịch vụ gì?</span>
            <select value={serviceId} onChange={(e) => onServiceId(e.target.value)} className={field}>
              <option value="">Chưa rõ, để bác sĩ xem</option>
              {services.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name} · khoảng {x.durationMinutes} phút
                </option>
              ))}
            </select>
          </label>
        )}

        <label className="block">
          <span className={fieldLabel}>
            Lý do khám <span className="font-normal text-[#999999]">(không bắt buộc)</span>
          </span>
          <textarea
            rows={4}
            value={reason}
            onChange={(e) => onReason(e.target.value)}
            placeholder="Ví dụ: bỏ ăn hai ngày, hay gãi tai, đi ngoài lỏng…"
            className={cn(field, "h-auto resize-y py-3")}
          />
        </label>

        <p className="flex gap-2.5 rounded-xl bg-[#fff1e3] px-4 py-3 text-[13px] leading-relaxed text-[#5b4a3e]">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-[#f57c1f]" />
          Đây là yêu cầu đặt lịch, chưa phải lịch đã chốt. Phòng khám sẽ xếp bác sĩ rảnh vào giờ bạn
          chọn và báo lại qua email; nếu không xếp được sẽ đề xuất giờ khác để bạn đồng ý hoặc huỷ.
        </p>
      </div>
    </section>
  );
}

/** Tóm tắt bám bên phải để người ta luôn thấy mình đã chọn gì, khỏi phải lùi lại xem. */
function BookingSummary({
  pet,
  date,
  startTime,
  step,
  service,
}: {
  pet: Pet | null;
  date: string;
  startTime: string;
  step: number;
  service: ClinicService | null;
}) {
  return (
    <aside className="lg:sticky lg:top-24 lg:self-start">
      <div className={cn(card, "overflow-hidden")}>
        <div className="flex items-center gap-2 bg-[linear-gradient(100deg,#fff6ee,#ffe6cf)] px-5 py-3.5">
          <CalendarDays aria-hidden className="size-4 text-[#f57c1f]" />
          <h2 className="text-[15px] font-bold text-[#1f4a3c]">Lịch của bạn</h2>
        </div>

        <div className="space-y-3.5 p-5 text-[14px]">
          <div className="flex items-center gap-3">
            {pet ? (
              <PetAvatar species={pet.species} photoUrl={pet.photoUrl} name={pet.name} small className="h-12 w-10 shrink-0" />
            ) : (
              <span className="grid size-10 shrink-0 place-items-center rounded-full bg-[#f4f1ee] text-[#bbbbbb]">
                <PawPrint aria-hidden className="size-5" />
              </span>
            )}
            <div className="min-w-0">
              <p className="text-[12px] text-[#999999]">Khám cho</p>
              <p className="truncate font-semibold text-[#242424]">{pet?.name ?? "Chưa chọn bé"}</p>
            </div>
          </div>

          <SummaryRow icon={Stethoscope} label="Dịch vụ" value={service?.name ?? "Để bác sĩ xem"} />
          <SummaryRow icon={CalendarDays} label="Ngày" value={step >= 1 ? formatDate(date) : "Chưa chọn"} />
          <SummaryRow icon={Clock3} label="Giờ mong muốn" value={startTime ? formatTime(startTime) : "Chưa chọn"} />
          <SummaryRow icon={MapPin} label="Tại" value={CLINIC.address} />
        </div>

        <p className="border-t border-[#f3ece4] px-5 py-3.5 text-[13px] text-[#777777]">
          Gửi xong bạn vẫn huỷ được, miễn là trước giờ khám.
        </p>
      </div>
    </aside>
  );
}

function SummaryRow({ icon: Icon, label, value }: { icon: typeof PawPrint; label: string; value: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="grid size-8 shrink-0 place-items-center rounded-full bg-[#fff1e3] text-[#f57c1f]">
        <Icon aria-hidden className="size-4" />
      </span>
      <div className="min-w-0">
        <p className="text-[12px] text-[#999999]">{label}</p>
        <p className="tnum font-medium text-[#242424]">{value}</p>
      </div>
    </div>
  );
}
