"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, CheckCircle2, ChevronLeft, ClipboardList, Clock3, FileText, MapPin, PawPrint, Pill, Stethoscope, UserRound } from "lucide-react";
import { ApiError, bookingApi, medicalRecordApi, myPetApi } from "@/lib/api";
import { formatDate, formatTime, todayISO } from "@/lib/utils/format";
import type { AppointmentStatus } from "@/types";
import { useToast } from "@/components/ui";
import { CLINIC } from "@/config/clinic";
import { useDoctorDirectory } from "@/lib/useDoctors";
import type { Appointment, MedicalRecord } from "@/types";
import { ButtonLink, Container, SiteButton } from "@/components/site/primitives";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { PetAvatar } from "@/components/site/PetAvatar";
import { SiteDialog } from "@/components/site/SiteDialog";
import { APPOINTMENT_LOOK, PRESCRIPTION_LOOK, StatusPill } from "@/components/site/StatusPill";

/** Lịch còn đổi hoặc huỷ được: chưa khám xong, chưa huỷ — gồm cả lịch đang chờ xếp bác sĩ. */
const OPEN_STATUSES: AppointmentStatus[] = [
  "AWAITING_ASSIGNMENT",
  "AWAITING_CUSTOMER",
  "PENDING",
  "CONFIRMED",
];

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
  const pets = useQuery({ queryKey: ["pets", "mine"], queryFn: myPetApi.list });
  const doctors = useDoctorDirectory();

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
          <ButtonLink href="/user/appointments" variant="outline">
            Về lịch khám của bạn
          </ButtonLink>
        </div>
      </Container>
    );
  }

  const pet = (pets.data ?? []).find((p) => p.id === appointment.petId) ?? null;
  const doctor = doctors.label(appointment.doctorUserId);
  const gender = pet?.gender === "MALE" ? "♂" : pet?.gender === "FEMALE" ? "♀" : null;

  return (
    <div className="space-y-4 md:space-y-5">
      <Link
        href="/user/appointments"
        className="inline-flex items-center gap-1.5 text-[15px] text-stone hover:text-pine"
      >
        <ChevronLeft aria-hidden className="size-4" />
        Lịch khám của bạn
      </Link>

      {/* Đầu trang: trạng thái, tiêu đề, ngày giờ & địa điểm */}
      <div className="rounded-2xl bg-white p-4 shadow-[0_2px_14px_-8px_rgba(0,0,0,0.12)] sm:p-5">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="font-[family-name:var(--font-brand)] text-[24px] leading-tight text-pine md:text-[30px]">
            {pet ? `Lịch khám của ${pet.name}` : "Lịch khám"}
          </h1>
          <StatusPill look={APPOINTMENT_LOOK[appointment.status]} />
        </div>
        <div className="mt-3 flex flex-wrap gap-x-6 gap-y-2 text-[14px] text-[#3b2a20]">
          <Meta icon={CalendarDays}>{formatDate(appointment.date)}</Meta>
          <Meta icon={Clock3}>
            {formatTime(appointment.startTime)} – {formatTime(appointment.endTime)}
            {!appointment.slotId && <span className="text-stone"> (giờ mong muốn)</span>}
          </Meta>
          <Meta icon={MapPin}>{CLINIC.address}</Meta>
        </div>
        <StatusNotice appointment={appointment} doctor={doctor} />
      </div>

      {/* Hàng 1: thú cưng · dịch vụ · bác sĩ */}
      <div className="grid gap-4 md:grid-cols-3 md:gap-5">
        <Card icon={PawPrint} title="Thông tin thú cưng">
          <div className="flex items-center gap-4">
            <PetAvatar species={pet?.species} photoUrl={pet?.photoUrl} name={pet?.name} small className="h-20 w-16 shrink-0" />
            <div className="min-w-0 text-[13px] text-[#5b4a3e]">
              <p className="text-[17px] font-bold text-pine">
                {pet?.name ?? "—"} {gender && <span className="text-[#f57c1f]">{gender}</span>}
              </p>
              {pet && (
                <>
                  <p>
                    {pet.species}
                    {pet.breed ? ` · ${pet.breed}` : ""}
                  </p>
                  {pet.dateOfBirth && <p>Ngày sinh: {formatDate(pet.dateOfBirth)}</p>}
                  {pet.weightKg && <p>Cân nặng: {pet.weightKg} kg</p>}
                </>
              )}
            </div>
          </div>
        </Card>

        <Card icon={ClipboardList} title="Thông tin dịch vụ">
          <div className="flex items-center gap-4">
            <span className="grid size-14 shrink-0 place-items-center rounded-2xl bg-[#e3f1e6] text-teal">
              <Stethoscope aria-hidden className="size-7" />
            </span>
            <div className="min-w-0">
              {/* VD-21: dịch vụ đã chọn; lịch hẹn đặt trước khi có danh mục thì không có. */}
              <p className="text-[16px] font-bold text-pine">{appointment.serviceName ?? "Khám theo yêu cầu"}</p>
              <p className="mt-0.5 line-clamp-3 text-[13px] text-[#5b4a3e]">
                {appointment.reason?.trim() || "Bạn không ghi lý do khi đặt lịch."}
              </p>
            </div>
          </div>
        </Card>

        <Card icon={UserRound} title="Thông tin bác sĩ">
          <div className="flex items-center gap-4">
            <span className="grid size-14 shrink-0 place-items-center rounded-full bg-[#fff1e3] text-[#f57c1f]">
              <Stethoscope aria-hidden className="size-7" />
            </span>
            <div className="min-w-0">
              <p className="text-[16px] font-bold text-pine">{doctor}</p>
              <p className="mt-0.5 text-[13px] text-[#5b4a3e]">
                {appointment.doctorUserId ? "Bác sĩ phụ trách lần khám" : "Phòng khám sẽ xếp bác sĩ cho bé"}
              </p>
            </div>
          </div>
        </Card>
      </div>

      {/* Hàng 2: kết quả khám · đơn thuốc */}
      <MedicalRecordSection appointment={appointment} />

      <CancelPanel appointment={appointment} />

    </div>
  );
}

function Meta({ icon: Icon, children }: { icon: typeof CalendarDays; children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Icon aria-hidden className="size-4 shrink-0 text-teal" />
      <span>{children}</span>
    </span>
  );
}

/** Nói rõ với khách lịch đang ở bước nào và họ phải làm gì tiếp (nếu phải làm gì). */
function StatusNotice({ appointment: a, doctor }: { appointment: Appointment; doctor: string }) {
  const qc = useQueryClient();
  const toast = useToast();

  const accept = useMutation({
    mutationFn: () => bookingApi.acceptProposal(a.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["appointments"] });
      toast.success("Đã xác nhận lịch khám");
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không xác nhận được lịch khám."),
  });

  if (a.status === "AWAITING_CUSTOMER") {
    return (
      <div className="mt-4 rounded-xl bg-peach px-4 py-3 text-[14px]">
        <p>
          Giờ bạn chọn
          {a.preferredStartTime && a.preferredDate
            ? ` (${formatTime(a.preferredStartTime)}, ${formatDate(a.preferredDate)})`
            : ""}{" "}
          không xếp được bác sĩ. Phòng khám đề xuất{" "}
          <strong>
            {formatTime(a.startTime)}, {formatDate(a.date)}
          </strong>{" "}
          với {doctor}.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <SiteButton loading={accept.isPending} onClick={() => accept.mutate()}>
            Đồng ý giờ này
          </SiteButton>
          <span className="text-stone">Không hợp thì huỷ lịch ở bên cạnh, hoặc gọi {CLINIC.phone}.</span>
        </div>
      </div>
    );
  }
  return null;
}

/**
 * Bệnh án của lần khám này. Khách hàng đọc được bệnh án của chính mình
 * (GET /medical-records/by-appointment/{id} của pet-service cho phép CUSTOMER), còn 404 nghĩa là
 * bác sĩ chưa lập — đó là trạng thái bình thường, không phải lỗi.
 */
function MedicalRecordSection({ appointment }: { appointment: Appointment }) {
  const record = useQuery({
    queryKey: ["medical-record", appointment.id],
    queryFn: () => medicalRecordApi.byAppointment(appointment.id).catch(() => null),
  });

  if (record.isLoading) {
    return <div className="h-40 animate-pulse rounded-2xl bg-white" />;
  }

  const data = record.data;

  if (!data) {
    return (
      <Card icon={FileText} title="Kết quả khám">
        <p className="rounded-xl bg-[#fdf6ee] px-4 py-3 text-[14px] text-[#5b4a3e]">
          {appointment.status === "COMPLETED"
            ? "Bác sĩ chưa lập bệnh án cho lần khám này."
            : "Sau khi khám xong, chẩn đoán và đơn thuốc của bác sĩ sẽ hiện ở đây."}
        </p>
      </Card>
    );
  }

  return <RecordBody record={data} />;
}

function RecordBody({ record }: { record: MedicalRecord }) {
  return (
    <div className="grid gap-4 md:grid-cols-[1.5fr_1fr] md:gap-5">
      <Card icon={FileText} title="Kết quả khám">
        <dl className="space-y-2.5">
          <CheckRow label="Chẩn đoán" value={record.diagnosis} />
          {record.treatment && <CheckRow label="Bác sĩ đã xử lý" value={record.treatment} />}
          {record.notes && <CheckRow label="Lời dặn" value={record.notes} />}
        </dl>
      </Card>

      <Card
        icon={Pill}
        title="Đơn thuốc"
        extra={record.prescriptionItems.length > 0 ? <StatusPill look={PRESCRIPTION_LOOK[record.status]} /> : undefined}
      >
        {record.prescriptionItems.length === 0 ? (
          <p className="rounded-xl bg-[#fdf6ee] px-4 py-3 text-[14px] text-[#5b4a3e]">Lần khám này không kê thuốc.</p>
        ) : (
          <>
            <ul className="space-y-2">
              {record.prescriptionItems.map((item) => (
                <li key={item.id} className="flex gap-3 rounded-xl bg-[#f4faf7] p-3">
                  <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-white text-[#f57c1f]">
                    <Pill aria-hidden className="size-5" />
                  </span>
                  <div className="min-w-0">
                    <p className="font-semibold text-pine">{item.medicationName}</p>
                    <p className="text-[13px] text-[#5b4a3e]">
                      {item.dosage}, {item.frequency}
                      {item.durationDays ? `, trong ${item.durationDays} ngày` : ""}
                    </p>
                    {item.notes && <p className="mt-0.5 text-[13px] text-stone">Lưu ý: {item.notes}</p>}
                  </div>
                </li>
              ))}
            </ul>
            {record.status === "PENDING" && (
              <p className="mt-3 rounded-xl bg-peach px-4 py-2.5 text-[13px]">
                Ghé quầy phòng khám trả tiền thuốc rồi nhận thuốc mang về.
              </p>
            )}
          </>
        )}
      </Card>
    </div>
  );
}

function CheckRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid grid-cols-[auto_6.5rem_1fr] items-start gap-2 text-[14px] sm:grid-cols-[auto_8rem_1fr]">
      <CheckCircle2 aria-hidden className="mt-0.5 size-4 text-teal" />
      <dt className="text-[#5b4a3e]">{label}</dt>
      <dd className="text-[#2b1d14]">
        <span className="mr-1 text-stone">:</span>
        {value}
      </dd>
    </div>
  );
}

function Card({
  icon: Icon,
  title,
  extra,
  children,
}: {
  icon: typeof CalendarDays;
  title: string;
  extra?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-2xl bg-white p-4 shadow-[0_2px_14px_-8px_rgba(0,0,0,0.12)] sm:p-5">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-[16px] font-bold text-pine">
          <span className="grid size-7 place-items-center rounded-full bg-[#fff1e3] text-[#f57c1f]">
            <Icon aria-hidden className="size-4" />
          </span>
          {title}
        </h2>
        {extra}
      </div>
      {children}
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
    OPEN_STATUSES.includes(appointment.status) && appointment.date >= todayISO();

  if (!cancellable) return null;

  return (
    <div>
      <div className="grid grid-cols-2 gap-2.5 sm:flex sm:justify-end">
        <SiteButton variant="outline" size="sm" onClick={() => setConfirming(true)}>
          Huỷ lịch khám
        </SiteButton>
        <ButtonLink href="/appointments/create" variant="teal" size="sm">
          Đặt lịch khác
        </ButtonLink>
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
          Huỷ xong giờ này nhường lại cho người khác. Bạn vẫn đặt lịch mới được bất cứ lúc nào.
        </p>
      </SiteDialog>
    </div>
  );
}
