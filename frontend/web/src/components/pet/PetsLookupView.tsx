"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ApiError, petLookupApi } from "@/lib/api";
import { speciesStripe } from "@/lib/utils/status";
import { formatAge, formatDate } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import {
  EmptyState,
  ErrorState,
  Skeleton,
  controlClass,
} from "@/components/ui";

/**
 * Tra cứu thú cưng cho bác sĩ, nhân viên và quản trị — cả ba dùng chung
 * `GET /profile/pets`. Lọc theo tên làm ở phía giao diện vì endpoint không nhận tham số
 * tìm kiếm; danh sách một phòng khám đủ nhỏ để làm vậy.
 *
 * Chưa có đường sang lịch sử khám: bệnh án hiện chỉ tra được theo từng lịch hẹn, không
 * theo thú cưng (VD-17).
 */
export function PetsLookupView() {
  const [keyword, setKeyword] = useState("");
  const pets = useQuery({ queryKey: ["pets", "all"], queryFn: petLookupApi.list });

  const rows = (pets.data ?? []).filter((p) => {
    if (!keyword.trim()) return true;
    const k = keyword.trim().toLowerCase();
    return (
      p.name.toLowerCase().includes(k) ||
      p.species.toLowerCase().includes(k) ||
      (p.breed ?? "").toLowerCase().includes(k)
    );
  });

  return (
    <>
      <div className="mb-4 max-w-sm">
        <label htmlFor="pet-kw" className="mb-1.5 block text-sm font-medium">
          Tìm theo tên, loài hoặc giống
        </label>
        <input
          id="pet-kw"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="Mun, mèo, Poodle…"
          className={`${controlClass} h-9`}
        />
      </div>

      {pets.isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
      ) : pets.isError ? (
        <ErrorState
          message={pets.error instanceof ApiError ? pets.error.message : "Không tải được danh sách."}
          onRetry={() => pets.refetch()}
        />
      ) : rows.length === 0 ? (
        <div className="rounded-[var(--radius-control)] border border-line bg-surface">
          <EmptyState
            title={keyword ? "Không có bé nào khớp" : "Chưa có thú cưng nào trong hệ thống"}
          />
        </div>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((pet) => (
            <li
              key={pet.id}
              className="flex overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface"
            >
              <span aria-hidden className={cn("w-1.5 shrink-0", speciesStripe(pet.species))} />
              <div className="min-w-0 flex-1 p-4">
                <h2 className="truncate font-medium text-ink">{pet.name}</h2>
                <p className="mt-0.5 text-sm text-bark">
                  {pet.species}
                  {pet.breed ? ` ${pet.breed}` : ""} · {formatAge(pet.dateOfBirth)}
                </p>
                <dl className="mt-3 space-y-1 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-bark">Giới tính</dt>
                    <dd>
                      {pet.gender === "MALE" ? "Đực" : pet.gender === "FEMALE" ? "Cái" : "Chưa rõ"}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-bark">Cân nặng</dt>
                    <dd className="tnum">{pet.weightKg ? `${pet.weightKg} kg` : "—"}</dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-bark">Ngày sinh</dt>
                    <dd>{formatDate(pet.dateOfBirth)}</dd>
                  </div>
                </dl>
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
