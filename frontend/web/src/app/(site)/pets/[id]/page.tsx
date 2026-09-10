"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ChevronLeft, Pencil, Trash2 } from "lucide-react";
import { ApiError, bookingApi, customerApi } from "@/lib/api";
import { formatAge, formatDate, formatTime } from "@/lib/utils/format";
import { useToast } from "@/components/ui";
import type { Appointment, Pet } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { PetFormDialog } from "@/components/site/PetFormDialog";
import { SiteDialog } from "@/components/site/SiteDialog";
import { APPOINTMENT_LOOK, StatusPill } from "@/components/site/StatusPill";

export default function PetDetailPage() {
  return (
    <CustomerOnly>
      <PetDetailBody />
    </CustomerOnly>
  );
}

function PetDetailBody() {
  const { id } = useParams<{ id: string }>();

  const pet = useQuery({ queryKey: ["pets", id], queryFn: () => customerApi.pet(id) });

  if (pet.isLoading) return <PetSkeleton />;

  if (pet.isError || !pet.data) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Không mở được hồ sơ này</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Có thể hồ sơ đã bị xoá, hoặc bé này không thuộc tài khoản của bạn.
        </p>
        <div className="mt-8">
          <ButtonLink href="/pets" variant="outline">
            Về danh sách thú cưng
          </ButtonLink>
        </div>
      </Container>
    );
  }

  return <PetProfile pet={pet.data} />;
}

function PetProfile({ pet }: { pet: Pet }) {
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const qc = useQueryClient();
  const toast = useToast();
  const router = useRouter();

  const remove = useMutation({
    mutationFn: () => customerApi.deletePet(pet.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["pets"] });
      toast.success(`Đã xoá hồ sơ ${pet.name}`);
      router.push("/pets");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không xoá được hồ sơ."),
  });

  const gender =
    pet.gender === "MALE" ? "Đực" : pet.gender === "FEMALE" ? "Cái" : "Chưa rõ";

  return (
    <>
      <div className="bg-mint">
        <Container className="pt-8">
          <Link
            href="/pets"
            className="inline-flex items-center gap-1.5 text-[15px] text-stone hover:text-pine"
          >
            <ChevronLeft aria-hidden className="size-4" />
            Thú cưng của bạn
          </Link>
        </Container>

        <Container className="grid gap-8 py-10 md:grid-cols-[auto_1fr] md:gap-12 md:py-14">
          <PetAvatar species={pet.species} className="h-52 w-40" />

          <div className="min-w-0">
            <h1 className="t-h2">{pet.name}</h1>
            <p className="t-lede mt-3 text-stone">
              {pet.species}
              {pet.breed ? ` ${pet.breed}` : ""}
            </p>

            <dl className="mt-8 grid max-w-xl grid-cols-2 gap-x-8 gap-y-5 sm:grid-cols-3">
              <Item label="Tuổi" value={formatAge(pet.dateOfBirth)} />
              <Item label="Giới tính" value={gender} />
              <Item
                label="Cân nặng"
                value={pet.weightKg ? `${pet.weightKg} kg` : "Chưa cân"}
              />
              <Item label="Ngày sinh" value={formatDate(pet.dateOfBirth)} />
              <Item label="Lập hồ sơ" value={formatDate(pet.createdAt)} />
            </dl>

            <div className="mt-9 flex flex-wrap gap-3">
              <ButtonLink href="/appointments/create">Đặt lịch khám cho bé</ButtonLink>
              <SiteButton variant="outline" onClick={() => setEditing(true)}>
                <Pencil aria-hidden className="size-4" />
                Sửa hồ sơ
              </SiteButton>
            </div>
          </div>
        </Container>
      </div>

      <Container className="py-14 md:py-20">
        <PetAppointments petId={pet.id} petName={pet.name} />

        <div className="mt-16 border-t border-mist pt-8">
          <button
            onClick={() => setConfirmDelete(true)}
            className="inline-flex items-center gap-2 text-[15px] text-stone underline underline-offset-4 hover:text-coral-deep"
          >
            <Trash2 aria-hidden className="size-4" />
            Xoá hồ sơ {pet.name}
          </button>
        </div>
      </Container>

      {editing && (
        <PetFormDialog open pet={pet} onClose={() => setEditing(false)} />
      )}

      <SiteDialog
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title={`Xoá hồ sơ ${pet.name}?`}
        description="Hồ sơ mất thì những lần khám đã ghi không tra lại theo bé được nữa."
        footer={
          <>
            <SiteButton variant="outline" onClick={() => setConfirmDelete(false)}>
              Giữ lại
            </SiteButton>
            <SiteButton loading={remove.isPending} onClick={() => remove.mutate()}>
              Xoá hồ sơ
            </SiteButton>
          </>
        }
      >
        <p className="text-stone">
          Nếu bé không còn ở với bạn nữa, bạn vẫn có thể giữ hồ sơ để xem lại lịch sử khám.
        </p>
      </SiteDialog>
    </>
  );
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[15px] text-stone">{label}</dt>
      <dd className="mt-0.5 font-medium">{value}</dd>
    </div>
  );
}

/**
 * Lịch khám của riêng bé này.
 *
 * TODO(backend, VD-17): không có endpoint lấy bệnh án theo thú cưng — bệnh án chỉ tra
 * được theo từng lịch hẹn. Nên đây lọc từ danh sách lịch hẹn của chính khách, và mỗi lần
 * khám dẫn sang trang chi tiết để xem chẩn đoán.
 * Cần bổ sung: GET /booking/pets/{petId}/medical-records.
 */
function PetAppointments({ petId, petName }: { petId: string; petName: string }) {
  const appointments = useQuery({
    queryKey: ["appointments", "mine"],
    queryFn: bookingApi.mine,
  });

  const list = (appointments.data ?? [])
    .filter((a) => a.petId === petId)
    .sort((a, b) => `${b.date}${b.startTime}`.localeCompare(`${a.date}${a.startTime}`));

  return (
    <section>
      <h2 className="t-h2">Những lần {petName} đã đến</h2>

      {appointments.isLoading ? (
        <div className="mt-8 space-y-3" aria-hidden>
          {[0, 1].map((i) => (
            <div key={i} className="h-20 animate-pulse rounded-[var(--radius-card)] bg-mint" />
          ))}
        </div>
      ) : list.length === 0 ? (
        <div className="mt-8 rounded-[var(--radius-card)] bg-mint px-6 py-12 text-center">
          <CalendarDays aria-hidden className="mx-auto size-7 text-teal" />
          <p className="t-h3 mt-4">Chưa có lần khám nào</p>
          <p className="measure mx-auto mt-2 text-stone">
            Khi bé đến khám, mỗi lần sẽ hiện ở đây kèm chẩn đoán của bác sĩ.
          </p>
          <div className="mt-6">
            <ButtonLink href="/appointments/create" variant="outline">
              Đặt lịch khám
            </ButtonLink>
          </div>
        </div>
      ) : (
        <ul className="mt-8 divide-y divide-mist border-y border-mist">
          {list.map((a) => (
            <AppointmentRow key={a.id} appointment={a} />
          ))}
        </ul>
      )}
    </section>
  );
}

function AppointmentRow({ appointment: a }: { appointment: Appointment }) {
  return (
    <li>
      <Link
        href={`/appointments/${a.id}`}
        className="flex flex-wrap items-center gap-x-6 gap-y-3 py-5 transition-colors hover:bg-mint/60"
      >
        <div className="min-w-40">
          <p className="font-medium">{formatDate(a.date)}</p>
          <p className="tnum text-[15px] text-stone">
            {formatTime(a.startTime)} đến {formatTime(a.endTime)}
          </p>
        </div>
        <p className="min-w-0 flex-1 truncate text-stone">
          {a.reason?.trim() || "Không ghi lý do khám"}
        </p>
        <StatusPill look={APPOINTMENT_LOOK[a.status]} />
      </Link>
    </li>
  );
}

function PetSkeleton() {
  return (
    <div className="bg-mint">
      <Container className="grid gap-8 py-14 md:grid-cols-[auto_1fr] md:gap-12">
        <div className="arch h-52 w-40 animate-pulse bg-white/60" />
        <div className="space-y-4 pt-2">
          <div className="h-10 w-1/3 animate-pulse rounded bg-white/60" />
          <div className="h-5 w-1/4 animate-pulse rounded bg-white/60" />
          <div className="h-24 w-full max-w-xl animate-pulse rounded bg-white/60" />
        </div>
      </Container>
    </div>
  );
}
