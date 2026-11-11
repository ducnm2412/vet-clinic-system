"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PawPrint, Plus } from "lucide-react";
import { ApiError, myPetApi } from "@/lib/api";
import type { Pet } from "@/types";
import { useToast } from "@/components/ui";
import { PetFormDialog } from "@/components/site/PetFormDialog";
import { PetCard } from "@/components/user/Cards";
import { ConfirmDialog } from "@/components/user/ConfirmDialog";
import { FilterDropdown, SearchInput } from "@/components/user/Controls";
import { EmptyState, ErrorState } from "@/components/user/EmptyState";
import { PageHeader } from "@/components/user/PageHeader";
import { btnPrimary } from "@/components/user/ui";

type Species = "ALL" | "Chó" | "Mèo" | "OTHER";

export default function MyPetsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  // Thao tác nhanh "Thêm thú cưng" ở dashboard trỏ tới /user/pets?add=1.
  const [adding, setAdding] = useState(
    () => typeof window !== "undefined" && new URLSearchParams(window.location.search).get("add") === "1",
  );
  const [editing, setEditing] = useState<Pet | null>(null);
  const [deleting, setDeleting] = useState<Pet | null>(null);
  const [q, setQ] = useState("");
  const [species, setSpecies] = useState<Species>("ALL");
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: myPetApi.list });

  const remove = useMutation({
    mutationFn: (id: string) => myPetApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["pets", "mine"] });
      toast.success("Đã xoá hồ sơ thú cưng");
      setDeleting(null);
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không xoá được hồ sơ."),
  });

  const all = pets.data ?? [];
  const list = all.filter((p) => {
    const sp = p.species.trim().toLowerCase();
    const okSpecies =
      species === "ALL" || (species === "OTHER" ? sp !== "chó" && sp !== "mèo" : sp === species.toLowerCase());
    const text = `${p.name} ${p.breed ?? ""}`.toLowerCase();
    return okSpecies && text.includes(q.trim().toLowerCase());
  });

  return (
    <>
      <PageHeader
        title="Thú cưng của tôi"
        description="Mỗi bé một hồ sơ — bác sĩ mở ra là thấy ngay tuổi, cân nặng và lịch sử khám."
        crumbs={[{ label: "Thú cưng của tôi" }]}
        action={
          <button type="button" onClick={() => setAdding(true)} className={btnPrimary}>
            <Plus aria-hidden className="size-4" /> Thêm thú cưng
          </button>
        }
      />

      {all.length > 0 && (
        <div className="mb-5 flex flex-col gap-3 sm:flex-row">
          <SearchInput value={q} onChange={setQ} placeholder="Tìm theo tên hoặc giống…" className="sm:max-w-xs sm:flex-1" />
          <FilterDropdown<Species>
            label="Lọc theo loài"
            value={species}
            onChange={setSpecies}
            className="sm:w-44"
            options={[
              { value: "ALL", label: "Tất cả loài" },
              { value: "Chó", label: "Chó" },
              { value: "Mèo", label: "Mèo" },
              { value: "OTHER", label: "Khác" },
            ]}
          />
        </div>
      )}

      {pets.isLoading ? (
        <ul className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3" aria-hidden>
          {[0, 1, 2].map((i) => (
            <li key={i} className="h-96 animate-pulse rounded-[20px] bg-[#fff5ec]" />
          ))}
        </ul>
      ) : pets.isError ? (
        <ErrorState title="Chưa mở được danh sách thú cưng" onRetry={() => pets.refetch()} />
      ) : all.length === 0 ? (
        <EmptyState
          icon={PawPrint}
          title="Chưa có bé nào trong hồ sơ"
          description="Thêm bé đầu tiên để đặt được lịch khám. Chỉ cần tên và loài là đủ."
          action={
            <button type="button" onClick={() => setAdding(true)} className={btnPrimary}>
              <Plus aria-hidden className="size-4" /> Thêm thú cưng
            </button>
          }
        />
      ) : list.length === 0 ? (
        <EmptyState icon={PawPrint} title="Không tìm thấy bé nào" description="Thử đổi từ khoá hoặc bộ lọc loài." />
      ) : (
        <ul className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {list.map((pet) => (
            <li key={pet.id}>
              <PetCard pet={pet} onEdit={() => setEditing(pet)} onDelete={() => setDeleting(pet)} />
            </li>
          ))}
        </ul>
      )}

      {adding && <PetFormDialog open pet={null} onClose={() => setAdding(false)} />}
      {editing && <PetFormDialog open pet={editing} onClose={() => setEditing(null)} />}

      <ConfirmDialog
        open={deleting !== null}
        title={`Xoá hồ sơ ${deleting?.name ?? ""}?`}
        description="Lịch sử khám của bé vẫn được phòng khám lưu lại. Thao tác này không hoàn tác được."
        confirmLabel="Xoá hồ sơ"
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
        onClose={() => setDeleting(null)}
      />
    </>
  );
}
