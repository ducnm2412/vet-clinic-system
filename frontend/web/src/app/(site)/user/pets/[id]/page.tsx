"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ChevronLeft, Pencil, TriangleAlert, Trash2 } from "lucide-react";
import { ApiError, bookingApi, myPetApi } from "@/lib/api";
import { formatAge, formatDate, formatTime } from "@/lib/utils/format";
import { useToast } from "@/components/ui";
import type { Appointment, MedicalRecord, Pet } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { PetFormDialog } from "@/components/site/PetFormDialog";
import { SiteDialog } from "@/components/site/SiteDialog";
import { APPOINTMENT_LOOK, StatusPill } from "@/components/site/StatusPill";
import { useDoctorDirectory } from "@/lib/useDoctors";

export default function PetDetailPage() {
  return (
    <CustomerOnly>
      <PetDetailBody />
    </CustomerOnly>
  );
}

function PetDetailBody() {
  const { id } = useParams<{ id: string }>();

  const pet = useQuery({ queryKey: ["pets", id], queryFn: () => myPetApi.get(id) });

  if (pet.isLoading) return <PetSkeleton />;

  if (pet.isError || !pet.data) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Không mở được hồ sơ này</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Có thể hồ sơ đã bị xoá, hoặc bé này không thuộc tài khoản của bạn.
        </p>
        <div className="mt-8">
          <ButtonLink href="/user/pets" variant="outline">
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
    mutationFn: () => myPetApi.remove(pet.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["pets"] });
      toast.success(`Đã xoá hồ sơ ${pet.name}`);
      router.push("/user/pets");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không xoá được hồ sơ."),
  });

  const gender =
    pet.gender === "MALE" ? "Đực" : pet.gender === "FEMALE" ? "Cái" : "Chưa rõ";

  return (
    <>
      <div className="rounded-3xl border border-[#f0e6dc] bg-white shadow-[0_4px_24px_-12px_rgba(0,0,0,0.12)]">
        <Container className="pt-8">
          <Link
            href="/user/pets"
            className="inline-flex items-center gap-1.5 text-[15px] text-stone hover:text-pine"
          >
            <ChevronLeft aria-hidden className="size-4" />
            Thú cưng của bạn
          </Link>
        </Container>

        <Container className="grid grid-cols-[5.5rem_1fr] gap-x-4 gap-y-5 py-6 md:grid-cols-[auto_1fr] md:gap-x-12 md:gap-y-0 md:py-14">
          <PetAvatar species={pet.species} photoUrl={pet.photoUrl} name={pet.name} className="h-28 w-[5.5rem] md:row-span-2 md:h-52 md:w-40" />

          <div className="min-w-0 self-center md:self-end">
            <h1 className="t-h2 text-[28px] md:text-[inherit]">{pet.name}</h1>
            <p className="mt-1 text-stone md:t-lede md:mt-3">
              {pet.species}
              {pet.breed ? ` ${pet.breed}` : ""}
            </p>
          </div>

          <div className="col-span-2 min-w-0 md:col-span-1 md:col-start-2">
            <dl className="grid max-w-xl grid-cols-2 gap-2.5 sm:grid-cols-3 md:mt-6 [&>*:last-child]:col-span-2 sm:[&>*:last-child]:col-span-1">
              <Item label="Tuổi" value={formatAge(pet.dateOfBirth)} />
              <Item label="Giới tính" value={gender} />
              <Item
                label="Cân nặng"
                value={pet.weightKg ? `${pet.weightKg} kg` : "Chưa cân"}
              />
              <Item label="Ngày sinh" value={formatDate(pet.dateOfBirth)} />
              <Item label="Lập hồ sơ" value={formatDate(pet.createdAt)} />
            </dl>

            {(pet.allergies || pet.notes) && (
              <div className="mt-5 max-w-xl space-y-3">
                {pet.allergies && (
                  <p className="flex gap-2 rounded-[var(--radius-card)] bg-[#fdf6ee] p-3 text-[15px]">
                    <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-coral-deep" />
                    <span>
                      <span className="font-medium">Dị ứng:</span> {pet.allergies}
                    </span>
                  </p>
                )}
                {pet.notes && <p className="text-[15px] text-stone">{pet.notes}</p>}
              </div>
            )}

            <div className="mt-5 flex flex-wrap gap-2 sm:gap-3 md:mt-9 max-sm:[&>*]:h-9 max-sm:[&>*]:gap-1.5 max-sm:[&>*]:px-3 max-sm:[&>*]:text-[13px]">
              <ButtonLink href="/appointments/create" size="sm">
                <CalendarDays aria-hidden className="size-4" />
                Đặt lịch khám
              </ButtonLink>
              <SiteButton variant="outline" size="sm" onClick={() => setEditing(true)}>
                <Pencil aria-hidden className="size-4" />
                Sửa hồ sơ
              </SiteButton>
              <SiteButton
                variant="outline"
                size="sm"
                onClick={() => setConfirmDelete(true)}
                className="hover:!border-coral-deep hover:!text-coral-deep"
              >
                <Trash2 aria-hidden className="size-4" />
                Xoá hồ sơ
              </SiteButton>
            </div>
          </div>
        </Container>
      </div>

      <Container className="py-8 md:py-20">
        <PetAppointments petId={pet.id} petName={pet.name} />

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
    <div className="rounded-xl bg-[#fdf6ee] px-3.5 py-2.5">
      <dt className="text-[13px] text-stone">{label}</dt>
      <dd className="mt-0.5 font-semibold">{value}</dd>
    </div>
  );
}

/**
 * Lịch khám của riêng bé này, kèm chẩn đoán từng lần (CN-24).
 *
 * Lịch hẹn vẫn lấy từ booking-service rồi lọc theo bé; chẩn đoán lấy một lượt từ
 * `GET /pets/me/{petId}/medical-records` của pet-service và ghép theo `appointmentId` — trước đây
 * không có đường nào tra bệnh án theo thú cưng, phải mở từng lịch hẹn mới thấy (VD-17).
 */
function PetAppointments({ petId, petName }: { petId: string; petName: string }) {
  const appointments = useQuery({
    queryKey: ["appointments", "mine"],
    queryFn: bookingApi.mine,
  });
  const records = useQuery({
    queryKey: ["pets", petId, "medical-records"],
    queryFn: () => myPetApi.history(petId),
  });
  const doctors = useDoctorDirectory();

  // Bệnh án tải hỏng thì danh sách lần khám vẫn hiện, chỉ thiếu dòng chẩn đoán.
  const diagnosisOf = new Map((records.data ?? []).map((r) => [r.appointmentId, r]));

  const list = (appointments.data ?? [])
    .filter((a) => a.petId === petId)
    .sort((a, b) => `${b.date}${b.startTime}`.localeCompare(`${a.date}${a.startTime}`));

  return (
    <section>
      <h2 className="truncate font-[family-name:var(--font-brand)] text-[20px] leading-tight tracking-[-0.015em] text-pine sm:text-[28px] md:text-[2.5rem]">Những lần {petName} đã đến</h2>

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
        <ul className="mt-5 space-y-2.5 md:mt-8">
          {list.map((a) => (
            <AppointmentRow
              key={a.id}
              appointment={a}
              doctor={doctors.label(a.doctorUserId)}
              record={diagnosisOf.get(a.id)}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function AppointmentRow({
  appointment: a,
  doctor,
  record,
}: {
  appointment: Appointment;
  doctor: string;
  record?: MedicalRecord;
}) {
  return (
    <li>
      <Link
        href={`/user/appointments/${a.id}`}
        className="flex gap-3.5 rounded-2xl border border-mist bg-white p-3.5 transition-colors hover:border-teal/40 hover:bg-mint/40 sm:gap-5 sm:p-4"
      >
        <div className="grid w-14 shrink-0 place-content-center rounded-xl bg-[#fdf6ee] py-2 text-center">
          <p className="tnum text-xl font-bold leading-none text-pine">{a.date.slice(8, 10)}</p>
          <p className="tnum mt-1 text-[11px] text-stone">{a.date.slice(5, 7)}/{a.date.slice(0, 4)}</p>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <p className="truncate font-semibold">{a.reason?.trim() || "Không ghi lý do khám"}</p>
            <span className="hidden shrink-0 sm:block">
              <StatusPill look={APPOINTMENT_LOOK[a.status]} />
            </span>
          </div>
          <p className="tnum mt-0.5 truncate text-[13px] text-stone">
            {formatTime(a.startTime)} – {formatTime(a.endTime)} · {doctor}
          </p>
          {record && (
            <p className="mt-1 line-clamp-2 text-[13px] text-pine">
              Chẩn đoán: {record.diagnosis}
              {record.prescriptionItems.length > 0 && (
                <span className="text-stone"> · {record.prescriptionItems.length} loại thuốc</span>
              )}
            </p>
          )}
          <div className="mt-2 origin-left scale-90 sm:hidden">
            <StatusPill look={APPOINTMENT_LOOK[a.status]} />
          </div>
        </div>
      </Link>
    </li>
  );
}

function PetSkeleton() {
  return (
    <div className="rounded-3xl border border-[#f0e6dc] bg-white">
      <Container className="grid gap-8 py-14 md:grid-cols-[auto_1fr] md:gap-12">
        <div className="arch h-52 w-40 animate-pulse bg-mint" />
        <div className="space-y-4 pt-2">
          <div className="h-10 w-1/3 animate-pulse rounded bg-mint" />
          <div className="h-5 w-1/4 animate-pulse rounded bg-mint" />
          <div className="h-24 w-full max-w-xl animate-pulse rounded bg-mint" />
        </div>
      </Container>
    </div>
  );
}
