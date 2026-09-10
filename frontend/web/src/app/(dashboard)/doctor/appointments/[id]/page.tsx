"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, bookingApi, medicalRecordApi } from "@/lib/api";
import { APPOINTMENT_STATUS, PRESCRIPTION_STATUS, speciesStripe } from "@/lib/utils/status";
import { formatAge, formatDate, formatTime } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { PrescriptionEditor } from "@/components/medical/PrescriptionEditor";
import {
  Button,
  ErrorState,
  Spinner,
  StatusTag,
  TextAreaField,
  useToast,
} from "@/components/ui";
import type { PrescriptionItemRequest } from "@/types";

/**
 * Màn hình khám. Sắp theo đúng thứ tự bác sĩ làm việc: xem con vật → đọc lý do khám →
 * ghi triệu chứng và chẩn đoán → kê thuốc. Mỗi bước một khối riêng thay vì một biểu mẫu
 * dài, để lúc đang khám nhìn phát là biết đang ở đâu.
 */
export default function DoctorAppointmentPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const toast = useToast();

  const appointment = useQuery({
    queryKey: ["appointments", id],
    queryFn: () => bookingApi.byId(id),
  });

  const record = useQuery({
    queryKey: ["medical-record", id],
    // Ca chưa lập bệnh án thì backend trả 404 — đó là trạng thái bình thường, không phải lỗi.
    queryFn: () => medicalRecordApi.byAppointment(id).catch(() => null),
  });

  const [diagnosis, setDiagnosis] = useState("");
  const [treatment, setTreatment] = useState("");
  const [notes, setNotes] = useState("");
  const [items, setItems] = useState<PrescriptionItemRequest[]>([]);

  // Nạp bệnh án đã có vào biểu mẫu; chỉ chạy khi dữ liệu về, không ghi đè lúc đang gõ.
  useEffect(() => {
    const data = record.data;
    if (!data) return;
    setDiagnosis(data.diagnosis);
    setTreatment(data.treatment ?? "");
    setNotes(data.notes ?? "");
    setItems(
      data.prescriptionItems.map((it) => ({
        medicationName: it.medicationName,
        dosage: it.dosage,
        frequency: it.frequency,
        durationDays: it.durationDays ?? undefined,
        notes: it.notes ?? undefined,
      })),
    );
  }, [record.data]);

  const save = useMutation({
    mutationFn: () =>
      medicalRecordApi.save(id, {
        diagnosis,
        treatment: treatment || undefined,
        notes: notes || undefined,
        prescriptionItems: items.length > 0 ? items : undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["medical-record", id] });
      qc.invalidateQueries({ queryKey: ["medical-records"] });
      toast.success(
        items.length > 0
          ? "Đã lưu bệnh án, phiếu thu tiền thuốc đã gửi sang quầy"
          : "Đã lưu bệnh án",
      );
    },
    onError: (err) =>
      toast.error(err instanceof ApiError ? err.message : "Không lưu được bệnh án."),
  });

  if (appointment.isLoading) return <Spinner label="Đang mở hồ sơ" />;
  if (appointment.isError || !appointment.data) {
    return (
      <ErrorState message="Không mở được ca khám này." onRetry={() => appointment.refetch()} />
    );
  }

  const a = appointment.data;
  const pet = a.pet;
  const existing = record.data;
  // Đơn thuốc đã thu tiền hoặc đã phát thì khoá lại — sửa lúc đó sẽ lệch với phiếu thu.
  const locked = existing != null && existing.status !== "PENDING";

  return (
    <>
      <Link
        href="/doctor/appointments"
        className="text-sm text-moss underline underline-offset-2"
      >
        ← Về danh sách ca khám
      </Link>

      <div className="mt-3 grid gap-5 lg:grid-cols-[20rem_1fr]">
        <aside className="space-y-4 lg:sticky lg:top-6 lg:self-start">
          <section className="overflow-hidden rounded-[var(--radius-control)] border border-line bg-surface">
            <div className="flex">
              {/* Dải màu theo loài — cùng quy ước với thẻ thú cưng ở các màn hình khác. */}
              <span aria-hidden className={cn("w-1.5 shrink-0", speciesStripe(pet?.species))} />
              <div className="min-w-0 flex-1 p-4">
                <h1 className="font-[family-name:var(--font-display)] text-[20px] text-ink">
                  {pet?.name ?? "Không rõ thú cưng"}
                </h1>
                {pet && (
                  <p className="mt-0.5 text-sm text-bark">
                    {pet.species}
                    {pet.breed ? ` ${pet.breed}` : ""} · {formatAge(pet.dateOfBirth)}
                  </p>
                )}
                {pet && (
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
                  </dl>
                )}
              </div>
            </div>
          </section>

          <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
            <h2 className="mb-2 font-medium text-ink">Ca khám</h2>
            <StatusTag status={APPOINTMENT_STATUS[a.status]} />
            <dl className="mt-3 space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-bark">Ngày</dt>
                <dd>{formatDate(a.date)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-bark">Giờ</dt>
                <dd className="tnum">
                  {formatTime(a.startTime)}–{formatTime(a.endTime)}
                </dd>
              </div>
            </dl>
            <div className="mt-3 border-t border-line pt-3">
              <p className="text-sm text-bark">Lý do khám</p>
              <p className="mt-0.5 text-sm text-ink">{a.reason || "Chủ nuôi không ghi"}</p>
            </div>
          </section>

          {existing && (
            <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
              <h2 className="mb-2 font-medium text-ink">Đơn thuốc</h2>
              <StatusTag status={PRESCRIPTION_STATUS[existing.status]} />
              <p className="mt-2 text-sm text-bark">
                {existing.status === "PENDING"
                  ? "Khách trả tiền ở quầy rồi mới nhận được thuốc."
                  : existing.status === "PAID"
                    ? "Đã thu tiền, chờ phát thuốc."
                    : "Khách đã nhận thuốc."}
              </p>
            </section>
          )}
        </aside>

        <div className="space-y-5">
          <Section title="Chẩn đoán" hint="Bắt buộc — đây là nội dung chính của bệnh án">
            <TextAreaField
              label="Kết luận sau khám"
              rows={3}
              required
              disabled={locked}
              value={diagnosis}
              onChange={(e) => setDiagnosis(e.target.value)}
            />
          </Section>

          <Section title="Điều trị">
            <TextAreaField
              label="Đã xử lý những gì"
              rows={3}
              disabled={locked}
              value={treatment}
              onChange={(e) => setTreatment(e.target.value)}
            />
          </Section>

          <Section title="Dặn dò chủ nuôi">
            <TextAreaField
              label="Ghi chú"
              rows={2}
              disabled={locked}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </Section>

          <Section
            title="Kê đơn"
            hint="Lưu bệnh án kèm thuốc sẽ tự tạo phiếu thu tiền ở quầy"
          >
            <PrescriptionEditor items={items} onChange={setItems} disabled={locked} />
          </Section>

          {locked ? (
            <p className="rounded-[var(--radius-control)] bg-amber-wash px-4 py-3 text-sm">
              Đơn thuốc đã chuyển sang quầy nên bệnh án khoá lại để không lệch với phiếu thu.
            </p>
          ) : (
            <div className="flex items-center gap-3">
              <Button
                loading={save.isPending}
                disabled={diagnosis.trim().length === 0}
                onClick={() => save.mutate()}
              >
                {existing ? "Lưu thay đổi" : "Lưu bệnh án"}
              </Button>
              {diagnosis.trim().length === 0 && (
                <p className="text-sm text-bark">Nhập chẩn đoán trước khi lưu.</p>
              )}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function Section({
  title,
  hint,
  children,
}: {
  title: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
      <div className="mb-3">
        <h2 className="font-medium text-ink">{title}</h2>
        {hint && <p className="mt-0.5 text-sm text-bark">{hint}</p>}
      </div>
      {children}
    </section>
  );
}
