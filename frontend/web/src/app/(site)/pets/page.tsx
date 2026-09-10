"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { customerApi } from "@/lib/api";
import { formatAge } from "@/lib/utils/format";
import type { Pet } from "@/types";
import { Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { PetFormDialog } from "@/components/site/PetFormDialog";

export default function PetsPage() {
  return (
    <CustomerOnly>
      <PetsBody />
    </CustomerOnly>
  );
}

function PetsBody() {
  const [adding, setAdding] = useState(false);
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: customerApi.pets });

  const list = pets.data ?? [];

  return (
    <>
      <div className="bg-mint py-14 md:py-20">
        <Container>
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div>
              <h1 className="t-h2">Thú cưng của bạn</h1>
              <p className="measure t-lede mt-4 text-stone">
                Mỗi bé một hồ sơ. Bác sĩ mở ra là thấy tuổi, cân nặng và những lần khám trước,
                không phải hỏi lại bạn từ đầu.
              </p>
            </div>
            <SiteButton onClick={() => setAdding(true)}>
              <Plus aria-hidden className="size-4" />
              Thêm thú cưng
            </SiteButton>
          </div>
        </Container>
      </div>

      <Container className="py-12 md:py-16">
        {pets.isLoading ? (
          <PetsSkeleton />
        ) : pets.isError ? (
          <div className="rounded-[var(--radius-card)] border border-mist px-6 py-14 text-center">
            <p className="t-h3">Chưa mở được danh sách thú cưng</p>
            <p className="mt-2 text-stone">Kết nối tới phòng khám đang trục trặc.</p>
            <SiteButton variant="outline" className="mt-6" onClick={() => pets.refetch()}>
              Thử lại
            </SiteButton>
          </div>
        ) : list.length === 0 ? (
          <div className="mx-auto max-w-lg py-10 text-center">
            <PetAvatar species={null} className="mx-auto h-48 w-36" />
            <h2 className="t-h2 mt-10">Chưa có bé nào trong hồ sơ</h2>
            <p className="mt-4 text-stone">
              Thêm bé đầu tiên để đặt được lịch khám. Chỉ cần tên và loài là đủ, còn lại điền
              sau cũng được.
            </p>
            <SiteButton size="lg" className="mt-8" onClick={() => setAdding(true)}>
              <Plus aria-hidden className="size-4" />
              Thêm thú cưng
            </SiteButton>
          </div>
        ) : (
          <ul className="grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
            {list.map((pet) => (
              <li key={pet.id}>
                <PetCard pet={pet} />
              </li>
            ))}
          </ul>
        )}
      </Container>

      {adding && <PetFormDialog open pet={null} onClose={() => setAdding(false)} />}
    </>
  );
}

function PetCard({ pet }: { pet: Pet }) {
  const gender =
    pet.gender === "MALE" ? "Đực" : pet.gender === "FEMALE" ? "Cái" : "Chưa rõ giới tính";

  return (
    <Link
      href={`/pets/${pet.id}`}
      className="group block rounded-[var(--radius-card)] border border-mist p-6 transition-[border-color,transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-teal hover:shadow-[var(--shadow-lift)]"
    >
      <div className="flex items-start gap-5">
        <PetAvatar species={pet.species} small className="h-20 w-16" />
        <div className="min-w-0">
          <h2 className="t-h3 truncate group-hover:text-teal-deep">{pet.name}</h2>
          <p className="mt-1 text-stone">
            {pet.species}
            {pet.breed ? ` ${pet.breed}` : ""}
          </p>
          <p className="text-stone">{formatAge(pet.dateOfBirth)}</p>
        </div>
      </div>

      <dl className="mt-6 grid grid-cols-2 gap-4 border-t border-mist pt-5 text-[15px]">
        <div>
          <dt className="text-stone">Giới tính</dt>
          <dd className="mt-0.5">{gender}</dd>
        </div>
        <div>
          <dt className="text-stone">Cân nặng</dt>
          <dd className="tnum mt-0.5">{pet.weightKg ? `${pet.weightKg} kg` : "Chưa cân"}</dd>
        </div>
      </dl>
    </Link>
  );
}

function PetsSkeleton() {
  return (
    <ul className="grid gap-8 sm:grid-cols-2 lg:grid-cols-3" aria-hidden>
      {[0, 1, 2].map((i) => (
        <li key={i} className="rounded-[var(--radius-card)] border border-mist p-6">
          <div className="flex gap-5">
            <div className="arch-sm h-20 w-16 animate-pulse bg-mint" />
            <div className="flex-1 space-y-2 pt-1">
              <div className="h-6 w-2/3 animate-pulse rounded bg-mint" />
              <div className="h-4 w-1/2 animate-pulse rounded bg-mint" />
            </div>
          </div>
          <div className="mt-6 h-12 animate-pulse rounded bg-mint" />
        </li>
      ))}
    </ul>
  );
}
