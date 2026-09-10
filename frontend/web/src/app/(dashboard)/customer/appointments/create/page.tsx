"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ApiError, bookingApi, customerApi, suggestionsFrom } from "@/lib/api";
import { speciesStripe } from "@/lib/utils/status";
import { formatAge, formatDate, formatTime, todayISO } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import type { SuggestedSlot } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  EmptyState,
  ErrorState,
  Spinner,
  TextAreaField,
  controlClass,
  useToast,
} from "@/components/ui";

/**
 * Đặt lịch: chọn thú cưng → chọn ngày → chọn khung giờ còn trống → ghi lý do.
 *
 * Không có bước chọn bác sĩ: backend tự gán slot và cũng không trả về bác sĩ nào phụ trách
 * (xem VD-16). Thà bỏ hẳn bước đó còn hơn hiện một danh sách bác sĩ mà chọn xong không có
 * tác dụng gì.
 */
export default function CreateAppointmentPage() {
  const router = useRouter();
  const toast = useToast();

  const [petId, setPetId] = useState("");
  const [date, setDate] = useState(todayISO());
  const [startTime, setStartTime] = useState("");
  const [reason, setReason] = useState("");
  const [suggestions, setSuggestions] = useState<SuggestedSlot[]>([]);

  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: customerApi.pets });

  const times = useQuery({
    queryKey: ["available-times", date],
    queryFn: () => bookingApi.availableTimes(date),
    enabled: date !== "",
  });

  const book = useMutation({
    mutationFn: () => bookingApi.create({ petId, date, startTime, reason: reason || undefined }),
    onSuccess: () => {
      toast.success("Đã đặt lịch khám");
      router.push("/customer/appointments");
    },
    onError: (err) => {
      // Khung giờ vừa bị người khác đặt mất: backend trả 409 kèm tối đa 3 gợi ý thay thế.
      const alt = suggestionsFrom(err);
      setSuggestions(alt);
      if (alt.length === 0) {
        toast.error(err instanceof ApiError ? err.message : "Không đặt được lịch.");
      }
    },
  });

  const canSubmit = petId !== "" && date !== "" && startTime !== "";

  if (pets.isLoading) return <Spinner label="Đang tải thú cưng" />;

  if ((pets.data ?? []).length === 0) {
    return (
      <>
        <PageHeader title="Đặt lịch khám" />
        <div className="rounded-[var(--radius-control)] border border-line bg-surface">
          <EmptyState
            title="Cần có hồ sơ thú cưng trước"
            description="Bác sĩ cần biết khám cho bé nào, nên hãy thêm thú cưng trước khi đặt lịch."
            action={
              <Link href="/customer/pets">
                <Button size="sm">Thêm thú cưng</Button>
              </Link>
            }
          />
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Đặt lịch khám"
        description="Chọn bé cần khám và khung giờ bạn tiện đến phòng khám."
      />

      <div className="max-w-2xl space-y-5">
        <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
          <h2 className="mb-3 font-medium text-ink">Khám cho bé nào</h2>
          <ul className="grid gap-2 sm:grid-cols-2">
            {(pets.data ?? []).map((pet) => (
              <li key={pet.id}>
                <button
                  onClick={() => setPetId(pet.id)}
                  aria-pressed={petId === pet.id}
                  className={cn(
                    "flex w-full overflow-hidden rounded-[var(--radius-control)] border text-left transition-colors",
                    petId === pet.id
                      ? "border-moss bg-moss-wash"
                      : "border-line-strong bg-surface hover:bg-paper",
                  )}
                >
                  <span aria-hidden className={cn("w-1.5 shrink-0", speciesStripe(pet.species))} />
                  <span className="min-w-0 flex-1 px-3 py-2.5">
                    <span className="block font-medium text-ink">{pet.name}</span>
                    <span className="block text-sm text-bark">
                      {pet.species} · {formatAge(pet.dateOfBirth)}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
          <h2 className="mb-3 font-medium text-ink">Ngày và giờ</h2>

          <div className="max-w-48">
            <label htmlFor="date" className="mb-1.5 block text-sm font-medium">
              Ngày khám
            </label>
            <input
              id="date"
              type="date"
              min={todayISO()}
              value={date}
              onChange={(e) => {
                setDate(e.target.value);
                setStartTime("");
                setSuggestions([]);
              }}
              className={`${controlClass} h-9`}
            />
          </div>

          <div className="mt-4">
            <p className="mb-2 text-sm font-medium">Khung giờ còn trống</p>

            {times.isLoading ? (
              <Spinner label="Đang xem giờ trống" />
            ) : times.isError ? (
              <ErrorState message="Không tải được khung giờ." onRetry={() => times.refetch()} />
            ) : (times.data ?? []).length === 0 ? (
              <p className="rounded-[var(--radius-control)] bg-paper px-3 py-3 text-sm text-bark">
                Ngày này đã kín chỗ. Chọn ngày khác giúp bạn nhé.
              </p>
            ) : (
              <ul className="flex flex-wrap gap-2">
                {(times.data ?? []).map((t) => (
                  <li key={t.startTime}>
                    <button
                      onClick={() => {
                        setStartTime(t.startTime);
                        setSuggestions([]);
                      }}
                      aria-pressed={startTime === t.startTime}
                      className={cn(
                        "rounded-[var(--radius-control)] border px-3 py-2 text-sm transition-colors",
                        startTime === t.startTime
                          ? "border-moss bg-moss-wash font-medium text-ink"
                          : "border-line-strong bg-surface text-ink-soft hover:bg-paper",
                      )}
                    >
                      <span className="tnum">{formatTime(t.startTime)}</span>
                      <span className="ml-1.5 text-xs text-bark">còn {t.availableCount}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
          <TextAreaField
            label="Lý do khám"
            rows={3}
            hint="Bé có biểu hiện gì? Ghi càng rõ bác sĩ càng chuẩn bị tốt."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </section>

        {suggestions.length > 0 && (
          <div className="rounded-[var(--radius-control)] border border-amber/40 bg-amber-wash p-4">
            <p className="text-sm text-ink">
              Khung giờ này vừa có người đặt mất. Vài giờ khác còn trống:
            </p>
            <ul className="mt-3 flex flex-wrap gap-2">
              {suggestions.map((s, i) => (
                <li key={i}>
                  <button
                    onClick={() => {
                      setDate(s.date);
                      setStartTime(s.startTime);
                      setSuggestions([]);
                    }}
                    className="rounded-[var(--radius-control)] border border-line-strong bg-surface px-3 py-2 text-sm hover:bg-paper"
                  >
                    <span className="tnum">{formatTime(s.startTime)}</span>
                    <span className="ml-1.5 text-bark">{formatDate(s.date)}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="flex items-center gap-3">
          <Button size="lg" loading={book.isPending} disabled={!canSubmit} onClick={() => book.mutate()}>
            Đặt lịch
          </Button>
          {!canSubmit && (
            <p className="text-sm text-bark">
              {petId === "" ? "Chọn thú cưng" : startTime === "" ? "Chọn khung giờ" : ""}
            </p>
          )}
        </div>
      </div>
    </>
  );
}
