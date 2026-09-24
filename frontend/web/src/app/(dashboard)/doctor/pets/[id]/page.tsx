"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ApiError, medicalRecordApi, petLookupApi } from "@/lib/api";
import { PRESCRIPTION_STATUS, speciesStripe } from "@/lib/utils/status";
import { formatAge, formatDate, formatDateTime } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import type { MedicalRecord } from "@/types";
import { Button, EmptyState, ErrorState, Spinner, StatusTag } from "@/components/ui";

/**
 * Lịch sử khám của 1 thú cưng — mở từ thẻ thú cưng ở trang "Thú cưng". Gộp mọi bệnh án
 * qua các lần khám (GET /booking/medical-records/by-pet/{petId}), mới nhất trước.
 */
export default function DoctorPetRecordsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  const pet = useQuery({ queryKey: ["pets", id], queryFn: () => petLookupApi.byId(id) });
  const records = useQuery({
    queryKey: ["medical-records", "by-pet", id],
    queryFn: () => medicalRecordApi.byPet(id),
  });

  if (pet.isLoading) return <Spinner label="Đang mở hồ sơ thú cưng" />;
  if (pet.isError || !pet.data) {
    return <ErrorState message="Không mở được hồ sơ thú cưng này." onRetry={() => pet.refetch()} />;
  }

  return (
    <>
      <Link href="/doctor/pets" className="text-sm text-moss underline underline-offset-2">
        ← Về danh sách thú cưng
      </Link>

      <div className="mt-3 grid gap-5 lg:grid-cols-[20rem_1fr]">
        <aside className="lg:sticky lg:top-6 lg:self-start">
          <section className="overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface">
            <div className="flex">
              <span aria-hidden className={cn("w-1.5 shrink-0", speciesStripe(pet.data.species))} />
              <div className="min-w-0 flex-1 p-4">
                <h1 className="font-[family-name:var(--font-display)] text-[20px] text-ink">
                  {pet.data.name}
                </h1>
                <p className="mt-0.5 text-sm text-bark">
                  {pet.data.species}
                  {pet.data.breed ? ` ${pet.data.breed}` : ""} · {formatAge(pet.data.dateOfBirth)}
                </p>
                <dl className="mt-3 space-y-1 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-bark">Giới tính</dt>
                    <dd>
                      {pet.data.gender === "MALE"
                        ? "Đực"
                        : pet.data.gender === "FEMALE"
                          ? "Cái"
                          : "Chưa rõ"}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-bark">Cân nặng</dt>
                    <dd className="tnum">{pet.data.weightKg ? `${pet.data.weightKg} kg` : "—"}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-bark">Ngày sinh</dt>
                    <dd>{formatDate(pet.data.dateOfBirth)}</dd>
                  </div>
                </dl>
              </div>
            </div>
          </section>
        </aside>

        <section>
          <h2 className="mb-3 font-medium text-ink">Lịch sử khám</h2>
          {records.isLoading ? (
            <Spinner label="Đang tải lịch sử khám" />
          ) : records.isError ? (
            <ErrorState
              message={
                records.error instanceof ApiError ? records.error.message : "Không tải được bệnh án."
              }
              onRetry={() => records.refetch()}
            />
          ) : (records.data ?? []).length === 0 ? (
            <div className="rounded-[var(--radius-control)] border border-line bg-surface">
              <EmptyState
                title="Chưa có bệnh án nào"
                description="Bé này chưa có lần khám nào được ghi lại."
              />
            </div>
          ) : (
            <ul className="space-y-3">
              {(records.data as MedicalRecord[]).map((r) => (
                <li
                  key={r.id}
                  className="rounded-[var(--radius-control)] border border-line bg-surface p-4"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <StatusTag status={PRESCRIPTION_STATUS[r.status]} />
                    <span className="text-sm text-bark">{formatDateTime(r.createdAt)}</span>
                  </div>
                  <p className="mt-2 text-sm font-medium text-ink">{r.diagnosis}</p>
                  {r.treatment && <p className="mt-1 text-sm text-bark">Điều trị: {r.treatment}</p>}
                  {r.notes && <p className="mt-1 text-sm text-bark">Dặn dò: {r.notes}</p>}
                  {r.prescriptionItems.length > 0 && (
                    <ul className="mt-2 space-y-1 text-sm text-bark">
                      {r.prescriptionItems.map((item) => (
                        <li key={item.id}>
                          {item.medicationName} · {item.dosage} · {item.frequency}
                          {item.durationDays ? ` · ${item.durationDays} ngày` : ""}
                        </li>
                      ))}
                    </ul>
                  )}
                  <div className="mt-3 flex justify-end">
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => router.push(`/doctor/appointments/${r.appointmentId}`)}
                    >
                      Mở hồ sơ ca khám
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
