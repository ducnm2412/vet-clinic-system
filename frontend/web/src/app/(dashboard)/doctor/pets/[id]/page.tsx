"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ApiError, petLookupApi } from "@/lib/api";
import { speciesStripe } from "@/lib/utils/status";
import { formatAge, formatDate } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import type { MedicalRecord } from "@/types";
import { MedicalRecordList } from "@/components/pet/PetHistory";
import { PetThumb } from "@/components/pet/PetThumb";
import { EmptyState, ErrorState, Spinner } from "@/components/ui";

/**
 * Lịch sử khám của 1 thú cưng — mở từ thẻ thú cưng ở trang "Thú cưng". Gộp mọi bệnh án
 * qua các lần khám (GET /pets/{petId}/medical-records), mới nhất trước.
 */
export default function DoctorPetRecordsPage() {
  const { id } = useParams<{ id: string }>();

  const pet = useQuery({ queryKey: ["pets", id], queryFn: () => petLookupApi.byId(id) });
  const records = useQuery({
    queryKey: ["medical-records", "by-pet", id],
    queryFn: () => petLookupApi.history(id),
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
                <div className="flex items-center gap-3">
                  <PetThumb pet={pet.data} className="size-14" />
                  <div className="min-w-0">
                    <h1 className="font-[family-name:var(--font-display)] text-[20px] text-ink">
                      {pet.data.name}
                    </h1>
                    <p className="mt-0.5 text-sm text-bark">
                      {pet.data.species}
                      {pet.data.breed ? ` ${pet.data.breed}` : ""} · {formatAge(pet.data.dateOfBirth)}
                    </p>
                  </div>
                </div>
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
            <MedicalRecordList records={records.data as MedicalRecord[]} />
          )}
        </section>
      </div>
    </>
  );
}
