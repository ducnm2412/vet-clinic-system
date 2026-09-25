"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, PawPrint, Search, UserPlus } from "lucide-react";
import { ApiError, authApi, bookingApi, clinicServiceApi, petLookupApi } from "@/lib/api";
import { formatDate, formatTime } from "@/lib/utils/format";
import type { CurrentUser, Pet } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  EmptyState,
  ErrorState,
  SelectField,
  Skeleton,
  TextAreaField,
  TextField,
  useToast,
} from "@/components/ui";

const todayISO = () => {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

/**
 * CN-19: lễ tân đặt lịch cho khách đang đứng ở quầy.
 *
 * Ba bước theo đúng thứ tự hỏi ngoài đời: khách là ai → bé nào → giờ nào. Khách chưa có tài khoản
 * hay chưa có hồ sơ thú cưng thì mở ngay tại chỗ, không bắt họ về nhà tự đăng ký rồi quay lại.
 */
export default function WalkInBookingPage() {
  const toast = useToast();
  const qc = useQueryClient();

  const [customer, setCustomer] = useState<CurrentUser | null>(null);
  const [pet, setPet] = useState<Pet | null>(null);
  const [date, setDate] = useState(todayISO());
  const [startTime, setStartTime] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [reason, setReason] = useState("");
  const [done, setDone] = useState<{ date: string; startTime: string } | null>(null);

  const times = useQuery({
    queryKey: ["available-times", date],
    queryFn: () => bookingApi.availableTimes(date),
    enabled: pet !== null,
  });
  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });

  const book = useMutation({
    mutationFn: () =>
      bookingApi.createWalkIn(customer!.id, {
        petId: pet!.id,
        date,
        startTime,
        serviceId: serviceId || undefined,
        reason: reason || undefined,
      }),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      setDone({ date: saved.date, startTime: saved.startTime });
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không đặt được lịch."),
  });

  function startOver() {
    setCustomer(null);
    setPet(null);
    setStartTime("");
    setServiceId("");
    setReason("");
    setDone(null);
  }

  if (done) {
    return (
      <>
        <PageHeader title="Đặt lịch tại quầy" description="Đã xong. Nhắc khách đến trước giờ hẹn khoảng 10 phút." />
        <section className="rounded-[var(--radius-control)] border border-line bg-surface p-6">
          <p className="flex items-center gap-2 font-medium text-ink">
            <Check aria-hidden className="size-5 text-teal" />
            Đã đặt lịch cho {pet?.name} lúc {formatTime(done.startTime)} ngày {formatDate(done.date)}
          </p>
          <p className="mt-2 text-sm text-bark">
            Khách: {customer?.firstName} {customer?.lastName} · {customer?.email}
          </p>
          <div className="mt-5">
            <Button onClick={startOver}>Đặt tiếp cho khách khác</Button>
          </div>
        </section>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Đặt lịch tại quầy"
        description="Cho khách đến trực tiếp. Khách chưa có tài khoản thì mở luôn ở bước một."
      />

      <div className="space-y-5">
        <StepCard step={1} title="Khách nào" done={customer !== null}>
          {customer ? (
            <ChosenRow
              title={`${customer.firstName} ${customer.lastName}`.trim()}
              subtitle={customer.email}
              onChange={() => {
                setCustomer(null);
                setPet(null);
              }}
            />
          ) : (
            <CustomerStep onPick={setCustomer} />
          )}
        </StepCard>

        {customer && (
          <StepCard step={2} title="Bé nào" done={pet !== null}>
            {pet ? (
              <ChosenRow
                title={pet.name}
                subtitle={`${pet.species}${pet.breed ? ` · ${pet.breed}` : ""}`}
                onChange={() => setPet(null)}
              />
            ) : (
              <PetStep customer={customer} onPick={setPet} />
            )}
          </StepCard>
        )}

        {customer && pet && (
          <StepCard step={3} title="Giờ nào" done={startTime !== ""}>
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label="Ngày khám"
                type="date"
                min={todayISO()}
                value={date}
                onChange={(e) => {
                  setDate(e.target.value);
                  setStartTime("");
                }}
              />
              <SelectField
                label="Dịch vụ"
                value={serviceId}
                onChange={(e) => setServiceId(e.target.value)}
                hint="Để trống nếu khách chưa rõ."
              >
                <option value="">Chưa rõ, để bác sĩ xem</option>
                {(services.data ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </SelectField>
            </div>

            <div className="mt-5">
              <p className="mb-2 text-sm font-medium">Khung giờ còn trống</p>
              {times.isLoading ? (
                <Skeleton className="h-10 w-full" />
              ) : times.isError ? (
                <ErrorState message="Không tải được khung giờ." onRetry={() => times.refetch()} />
              ) : (times.data ?? []).length === 0 ? (
                <p className="text-sm text-bark">Ngày này không còn chỗ. Chọn ngày khác giúp khách.</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {(times.data ?? []).map((t) => (
                    <button
                      key={t.startTime}
                      type="button"
                      aria-pressed={startTime === t.startTime}
                      onClick={() => setStartTime(t.startTime)}
                      className={`rounded-full border px-3 py-1.5 text-sm transition-colors ${
                        startTime === t.startTime
                          ? "border-teal bg-teal text-white"
                          : "border-line bg-surface text-ink hover:border-teal"
                      }`}
                    >
                      {formatTime(t.startTime)}
                      <span className="ml-1.5 text-xs opacity-70">còn {t.availableCount}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="mt-5">
              <TextAreaField
                label="Lý do khám"
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Khách kể gì thì ghi lại, bác sĩ đọc trước khi vào khám."
              />
            </div>

            <div className="mt-5">
              <Button disabled={!startTime} loading={book.isPending} onClick={() => book.mutate()}>
                Đặt lịch
              </Button>
            </div>
          </StepCard>
        )}
      </div>
    </>
  );
}

function StepCard({
  step,
  title,
  done,
  children,
}: {
  step: number;
  title: string;
  done: boolean;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-[var(--radius-control)] border border-line bg-surface p-5">
      <h2 className="mb-4 flex items-center gap-2.5 font-medium text-ink">
        <span
          aria-hidden
          className={`grid size-6 shrink-0 place-items-center rounded-full text-xs ${
            done ? "bg-teal text-white" : "bg-line text-bark"
          }`}
        >
          {done ? <Check className="size-3.5" /> : step}
        </span>
        {title}
      </h2>
      {children}
    </section>
  );
}

function ChosenRow({
  title,
  subtitle,
  onChange,
}: {
  title: string;
  subtitle: string;
  onChange: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="min-w-0">
        <p className="font-medium text-ink">{title}</p>
        <p className="text-sm text-bark">{subtitle}</p>
      </div>
      <Button variant="ghost" size="sm" onClick={onChange}>
        <ArrowLeft aria-hidden className="size-3.5" />
        Chọn lại
      </Button>
    </div>
  );
}

/** Tìm khách có sẵn, hoặc mở tài khoản mới ngay tại quầy. */
function CustomerStep({ onPick }: { onPick: (c: CurrentUser) => void }) {
  const toast = useToast();
  const [keyword, setKeyword] = useState("");
  const [applied, setApplied] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", password: "", phone: "" });

  const found = useQuery({
    queryKey: ["counter-customers", applied],
    queryFn: () => authApi.searchCustomers(applied || undefined),
  });

  const create = useMutation({
    mutationFn: () => authApi.createCustomerAccount(form),
    onSuccess: (saved) => {
      toast.success(`Đã mở tài khoản cho ${saved.firstName} ${saved.lastName}`.trim());
      onPick(saved);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không mở được tài khoản."),
  });

  if (creating) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Họ"
            value={form.firstName}
            onChange={(e) => setForm({ ...form, firstName: e.target.value })}
          />
          <TextField
            label="Tên"
            value={form.lastName}
            onChange={(e) => setForm({ ...form, lastName: e.target.value })}
          />
          <TextField
            label="Email"
            type="email"
            hint="Khách cần email để sau này tự đăng nhập xem lịch và bệnh án."
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <TextField
            label="Số điện thoại"
            inputMode="numeric"
            placeholder="0912345678"
            value={form.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })}
          />
          <TextField
            label="Mật khẩu tạm"
            hint="Ít nhất 8 ký tự. Đọc lại cho khách và nhắc họ đổi khi đăng nhập lần đầu."
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button loading={create.isPending} onClick={() => create.mutate()}>
            Mở tài khoản
          </Button>
          <Button variant="secondary" onClick={() => setCreating(false)}>
            Quay lại tìm
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setApplied(keyword.trim());
        }}
        role="search"
      >
        <div className="min-w-0 flex-1 basis-64">
          <TextField
            label="Tìm khách theo tên hoặc email"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary">
          <Search aria-hidden className="size-4" />
          Tìm
        </Button>
        <Button variant="ghost" onClick={() => setCreating(true)}>
          <UserPlus aria-hidden className="size-4" />
          Khách mới
        </Button>
      </form>

      {found.isLoading ? (
        <Skeleton className="h-20 w-full" />
      ) : found.isError ? (
        <ErrorState message="Không tìm được khách." onRetry={() => found.refetch()} />
      ) : (found.data?.content ?? []).length === 0 ? (
        <EmptyState
          title="Không có khách nào khớp"
          description="Khách đến lần đầu thì bấm Khách mới để mở tài khoản ngay."
        />
      ) : (
        <ul className="divide-y divide-line rounded-[var(--radius-control)] border border-line">
          {(found.data?.content ?? []).map((c) => (
            <li key={c.id}>
              <button
                type="button"
                onClick={() => onPick(c)}
                className="flex w-full items-center justify-between gap-3 p-3 text-left transition-colors hover:bg-canvas"
              >
                <span className="min-w-0">
                  <span className="block font-medium text-ink">{`${c.firstName} ${c.lastName}`.trim()}</span>
                  <span className="block text-sm text-bark">{c.email}</span>
                </span>
                {c.status === "LOCKED" && <span className="text-sm text-amber">Đang khoá</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Thú cưng của khách vừa chọn; chưa có con nào thì lập hồ sơ ngay. */
function PetStep({ customer, onPick }: { customer: CurrentUser; onPick: (p: Pet) => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ name: "", species: "", breed: "" });

  const pets = useQuery({
    queryKey: ["pets", "by-owner", customer.id],
    queryFn: () => petLookupApi.byOwners([customer.id]),
  });

  const add = useMutation({
    mutationFn: () =>
      petLookupApi.createFor(customer.id, {
        name: form.name,
        species: form.species,
        breed: form.breed || undefined,
        gender: "UNKNOWN",
      }),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ["pets"] });
      toast.success(`Đã lập hồ sơ cho ${saved.name}`);
      onPick(saved);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lập được hồ sơ."),
  });

  if (adding) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Tên bé" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <TextField
            label="Loài"
            placeholder="Chó, Mèo…"
            value={form.species}
            onChange={(e) => setForm({ ...form, species: e.target.value })}
          />
          <TextField
            label="Giống"
            value={form.breed}
            onChange={(e) => setForm({ ...form, breed: e.target.value })}
          />
        </div>
        <p className="text-sm text-bark">
          Cân nặng, ngày sinh và dị ứng để khách tự bổ sung sau, hoặc bác sĩ ghi lúc khám.
        </p>
        <div className="flex flex-wrap gap-2">
          <Button loading={add.isPending} onClick={() => add.mutate()}>
            Lập hồ sơ
          </Button>
          <Button variant="secondary" onClick={() => setAdding(false)}>
            Quay lại
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {pets.isLoading ? (
        <Skeleton className="h-16 w-full" />
      ) : pets.isError ? (
        <ErrorState message="Không tải được danh sách thú cưng." onRetry={() => pets.refetch()} />
      ) : (pets.data ?? []).length === 0 ? (
        <EmptyState title="Khách chưa có hồ sơ thú cưng nào" description="Lập một hồ sơ để đặt lịch." />
      ) : (
        <ul className="divide-y divide-line rounded-[var(--radius-control)] border border-line">
          {(pets.data ?? []).map((p) => (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => onPick(p)}
                className="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-canvas"
              >
                <PawPrint aria-hidden className="size-4 shrink-0 text-bark" />
                <span className="min-w-0">
                  <span className="block font-medium text-ink">{p.name}</span>
                  <span className="block text-sm text-bark">
                    {p.species}
                    {p.breed ? ` · ${p.breed}` : ""}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      <Button variant="ghost" onClick={() => setAdding(true)}>
        <PawPrint aria-hidden className="size-4" />
        Thêm thú cưng cho khách
      </Button>
    </div>
  );
}
