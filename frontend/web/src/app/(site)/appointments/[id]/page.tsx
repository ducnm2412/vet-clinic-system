"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, ChevronLeft, Clock3, MapPin, Pill } from "lucide-react";
import { ApiError, bookingApi, customerApi, medicalRecordApi } from "@/lib/api";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import { useToast } from "@/components/ui";
import { CLINIC } from "@/config/clinic";
import type { Appointment, MedicalRecord } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { SiteDialog } from "@/components/site/SiteDialog";
import { APPOINTMENT_LOOK, PRESCRIPTION_LOOK, StatusPill } from "@/components/site/StatusPill";

export default function AppointmentDetailPage() {
  return (
    <CustomerOnly>
      <AppointmentDetailBody />
    </CustomerOnly>
  );
}

/*
  Khách hàng KHÔNG gọi được GET /booking/appointments/{id} — endpoint đó chặn ở
  DOCTOR/STAFF/ADMIN. Nên trang này lấy lịch hẹn từ danh sách của chính mình rồi lọc ra,
  thay vì gọi một endpoint chắc chắn trả 403.
*/
function AppointmentDetailBody() {
  const { id } = useParams<{ id: string }>();

  const appointments = useQuery({ queryKey: ["appointments", "mine"], queryFn: bookingApi.mine });
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: customerApi.pets });

  const appointment = (appointments.data ?? []).find((a) => a.id === id) ?? null;

  if (appointments.isLoading) {
    return (
      <Container className="py-20">
        <div className="h-72 animate-pulse rounded-[var(--radius-card)] bg-mint" />
      </Container>
    );
  }

  if (appointments.isError || !appointment) {
    return (
      <Container className="py-24 text-center">
        <h1 className="t-h2">Không mở được lịch khám này</h1>
        <p className="measure mx-auto mt-4 text-stone">
          Lịch hẹn có thể đã bị xoá, hoặc không thuộc tài khoản của bạn.
        </p>
        <div className="mt-8">
          <ButtonLink href="/appointments" variant="outline">
            Về lịch khám của bạn
          </ButtonLink>
        </div>
      </Container>
    );
  }

  const pet = (pets.data ?? []).find((p) => p.id === appointment.petId) ?? null;

  return (
    <>
      <div className="bg-mint">
        <Container className="pt-8">
          <Link
            href="/appointments"
            className="inline-flex items-center gap-1.5 text-[15px] text-stone hover:text-pine"
          >
            <ChevronLeft aria-hidden className="size-4" />
            Lịch khám của bạn
          </Link>
        </Container>

        <Container className="grid gap-8 py-10 md:grid-cols-[auto_1fr] md:gap-12 md:py-14">
          <PetAvatar species={pet?.species} className="h-44 w-34" />

          <div className="min-w-0">
            <StatusPill look={APPOINTMENT_LOOK[appointment.status]} />
            <h1 className="t-h2 mt-4">
              {pet ? `Lịch khám của ${pet.name}` : "Lịch khám"}
            </h1>

            <dl className="mt-8 grid max-w-2xl gap-6 sm:grid-cols-3">
              <IconItem icon={CalendarDays} label="Ngày khám" value={formatDate(appointment.date)} />
              <IconItem
                icon={Clock3}
                label="Giờ"
                value={`${formatTime(appointment.startTime)} đến ${formatTime(appointment.endTime)}`}
              />
              <IconItem icon={MapPin} label="Tại" value={CLINIC.address} />
            </dl>

            {pet && (
              <p className="mt-6 text-stone">
                <Link href={`/pets/${pet.id}`} className="text-teal-deep underline underline-offset-4">
                  Xem hồ sơ của {pet.name}
                </Link>
              </p>
            )}
          </div>
        </Container>
      </div>

      <Container className="grid gap-12 py-14 lg:grid-cols-[1fr_20rem] lg:gap-16 md:py-20">
        <div className="min-w-0 space-y-12">
          <section>
            <h2 className="t-h3">Lý do khám bạn đã ghi</h2>
            <p className="measure t-body mt-3 text-stone">
              {appointment.reason?.trim() || "Bạn không ghi lý do khi đặt lịch."}
            </p>
          </section>

          <MedicalRecordSection appointment={appointment} />
        </div>

        <CancelPanel appointment={appointment} />
      </Container>
    </>
  );
}

function IconItem({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof CalendarDays;
  label: string;
  value: string;
}) {
  return (
    <div className="flex gap-3">
      <Icon aria-hidden className="mt-0.5 size-5 shrink-0 text-teal" />
      <div className="min-w-0">
        <dt className="text-[15px] text-stone">{label}</dt>
        <dd className="mt-0.5 font-medium">{value}</dd>
      </div>
    </div>
  );
}

/**
 * Bệnh án của lần khám này. Khách hàng đọc được bệnh án của chính mình
 * (GET /booking/appointments/{id}/medical-record cho phép CUSTOMER), còn 404 nghĩa là
 * bác sĩ chưa lập — đó là trạng thái bình thường, không phải lỗi.
 */
function MedicalRecordSection({ appointment }: { appointment: Appointment }) {
  const record = useQuery({
    queryKey: ["medical-record", appointment.id],
    queryFn: () => medicalRecordApi.byAppointment(appointment.id).catch(() => null),
  });

  if (record.isLoading) {
    return <div className="h-40 animate-pulse rounded-[var(--radius-card)] bg-mint" />;
  }

  const data = record.data;

  if (!data) {
    return (
      <section>
        <h2 className="t-h3">Kết quả khám</h2>
        <p className="measure mt-3 text-stone">
          {appointment.status === "COMPLETED"
            ? "Bác sĩ chưa lập bệnh án cho lần khám này."
            : "Sau khi khám xong, chẩn đoán và đơn thuốc của bác sĩ sẽ hiện ở đây."}
        </p>
      </section>
    );
  }

  return <RecordBody record={data} />;
}

function RecordBody({ record }: { record: MedicalRecord }) {
  return (
    <section>
      <h2 className="t-h3">Kết quả khám</h2>

      <dl className="mt-5 space-y-6">
        <div>
          <dt className="text-[15px] text-stone">Chẩn đoán</dt>
          <dd className="measure t-body mt-1">{record.diagnosis}</dd>
        </div>
        {record.treatment && (
          <div>
            <dt className="text-[15px] text-stone">Bác sĩ đã xử lý</dt>
            <dd className="measure t-body mt-1">{record.treatment}</dd>
          </div>
        )}
        {record.notes && (
          <div>
            <dt className="text-[15px] text-stone">Dặn dò</dt>
            <dd className="measure t-body mt-1">{record.notes}</dd>
          </div>
        )}
      </dl>

      {record.prescriptionItems.length > 0 && (
        <div className="mt-10">
          <div className="flex flex-wrap items-center gap-4">
            <h3 className="t-h3 flex items-center gap-2">
              <Pill aria-hidden className="size-5 text-teal" />
              Đơn thuốc
            </h3>
            <StatusPill look={PRESCRIPTION_LOOK[record.status]} />
          </div>

          <ul className="mt-5 divide-y divide-mist border-y border-mist">
            {record.prescriptionItems.map((item) => (
              <li key={item.id} className="py-4">
                <p className="font-medium">{item.medicationName}</p>
                <p className="mt-1 text-stone">
                  {item.dosage}, {item.frequency}
                  {item.durationDays ? `, trong ${item.durationDays} ngày` : ""}
                </p>
                {item.notes && <p className="mt-1 text-[15px] text-stone">{item.notes}</p>}
              </li>
            ))}
          </ul>

          {record.status === "PENDING" && (
            <p className="mt-5 rounded-[var(--radius-card)] bg-peach px-5 py-4 text-[15px]">
              Ghé quầy phòng khám trả tiền thuốc rồi nhận thuốc mang về.
            </p>
          )}
        </div>
      )}
    </section>
  );
}

function CancelPanel({ appointment }: { appointment: Appointment }) {
  const [confirming, setConfirming] = useState(false);
  const qc = useQueryClient();
  const toast = useToast();

  const cancel = useMutation({
    mutationFn: () => bookingApi.cancel(appointment.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      toast.success("Đã huỷ lịch khám");
      setConfirming(false);
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không huỷ được lịch khám."),
  });

  // Chỉ huỷ được lịch chưa diễn ra và chưa bị đóng. Luật thật nằm ở backend; đây chỉ để
  // biết có nên hiện nút hay không.
  const cancellable =
    (appointment.status === "PENDING" || appointment.status === "CONFIRMED") &&
    appointment.date >= todayISO();

  return (
    <aside className="lg:sticky lg:top-24 lg:self-start">
      <div className="rounded-[var(--radius-card)] bg-mint p-6">
        <h2 className="t-h3">Cần đổi kế hoạch?</h2>

        {cancellable ? (
          <>
            <p className="mt-3 text-stone">
              Huỷ giúp phòng khám nhường chỗ cho người khác. Muốn đổi giờ thì huỷ rồi đặt lại.
            </p>
            <SiteButton variant="outline" className="mt-6 w-full" onClick={() => setConfirming(true)}>
              Huỷ lịch khám
            </SiteButton>
            <ButtonLink href="/appointments/create" variant="teal" className="mt-3 w-full">
              Đặt lịch khác
            </ButtonLink>
          </>
        ) : (
          <>
            <p className="mt-3 text-stone">
              {appointment.status === "CANCELLED"
                ? "Lịch này đã huỷ."
                : appointment.status === "COMPLETED"
                  ? "Lần khám này đã xong."
                  : "Lịch này không huỷ được nữa."}{" "}
              Gọi {CLINIC.phone} nếu bạn cần phòng khám hỗ trợ.
            </p>
            <ButtonLink href="/appointments/create" className="mt-6 w-full">
              Đặt lịch mới
            </ButtonLink>
          </>
        )}
      </div>

      <SiteDialog
        open={confirming}
        onClose={() => setConfirming(false)}
        title="Huỷ lịch khám này?"
        description={`Ngày ${formatDate(appointment.date)}, lúc ${formatTime(appointment.startTime)}.`}
        footer={
          <>
            <SiteButton variant="outline" onClick={() => setConfirming(false)}>
              Giữ lịch
            </SiteButton>
            <SiteButton loading={cancel.isPending} onClick={() => cancel.mutate()}>
              Huỷ lịch
            </SiteButton>
          </>
        }
      >
        <p className="text-stone">
          Huỷ xong khung giờ này mở lại cho người khác. Bạn vẫn đặt lịch mới được bất cứ lúc nào.
        </p>
      </SiteDialog>
    </aside>
  );
}
