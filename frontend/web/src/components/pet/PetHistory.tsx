"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { TriangleAlert } from "lucide-react";
import { ApiError, petLookupApi } from "@/lib/api";
import { PRESCRIPTION_STATUS } from "@/lib/utils/status";
import { formatAge, formatDateTime } from "@/lib/utils/format";
import type { MedicalRecord, Pet } from "@/types";
import { PetThumb } from "@/components/pet/PetThumb";
import { Button, Dialog, EmptyState, ErrorState, Spinner, StatusTag, Tag } from "@/components/ui";

/**
 * Danh sách bệnh án của một thú cưng, dùng ở trang hồ sơ thú cưng và ở hộp thoại trên trang khám.
 *
 * `currentAppointmentId`: bệnh án của đúng ca đang khám được đánh dấu và không có nút mở lại ca đó.
 * `openInNewTab`: hộp thoại trên trang khám mở ca khác ở tab mới, để không bỏ trang đang có bản nháp.
 */
export function MedicalRecordList({
  records,
  currentAppointmentId,
  openInNewTab = false,
}: {
  records: MedicalRecord[];
  currentAppointmentId?: string;
  openInNewTab?: boolean;
}) {
  const router = useRouter();

  return (
    <ul className="space-y-3">
      {records.map((r) => {
        const isCurrent = r.appointmentId === currentAppointmentId;
        return (
          <li key={r.id} className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="flex flex-wrap items-center gap-2">
                <StatusTag status={PRESCRIPTION_STATUS[r.status]} />
                {isCurrent && <Tag>Ca khám này</Tag>}
              </span>
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
            {!isCurrent && (
              <div className="mt-3 flex justify-end">
                {openInNewTab ? (
                  <Link
                    href={`/doctor/appointments/${r.appointmentId}`}
                    target="_blank"
                    rel="noopener"
                    className="text-sm text-moss underline underline-offset-2"
                  >
                    Mở hồ sơ ca khám (tab mới)
                  </Link>
                ) : (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => router.push(`/doctor/appointments/${r.appointmentId}`)}
                  >
                    Mở hồ sơ ca khám
                  </Button>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Hồ sơ bệnh án của thú cưng, hiện ngay trên trang khám. Là hộp thoại chứ không chuyển trang: bác sĩ
 * đang gõ dở chẩn đoán hay đơn thuốc, rời trang là mất bản nháp chưa lưu.
 */
export function PetHistoryDialog({
  pet,
  open,
  onClose,
  currentAppointmentId,
}: {
  pet: Pet;
  open: boolean;
  onClose: () => void;
  currentAppointmentId?: string;
}) {
  // Chỉ gọi khi hộp thoại mở: phần lớn ca khám bác sĩ không cần xem lịch sử.
  const records = useQuery({
    queryKey: ["medical-records", "by-pet", pet.id],
    queryFn: () => petLookupApi.history(pet.id),
    enabled: open,
  });

  return (
    <Dialog
      wide
      open={open}
      onClose={onClose}
      title={`Hồ sơ bệnh án — ${pet.name}`}
      description={`${pet.species}${pet.breed ? ` ${pet.breed}` : ""} · ${formatAge(pet.dateOfBirth)}`}
      footer={
        <>
          <Link
            href={`/doctor/pets/${pet.id}`}
            target="_blank"
            rel="noopener"
            className="mr-auto self-center text-sm text-moss underline underline-offset-2"
          >
            Xem toàn bộ hồ sơ (tab mới)
          </Link>
          <Button variant="secondary" onClick={onClose}>
            Đóng
          </Button>
        </>
      }
    >
      <div className="mb-4 flex items-center gap-3">
        <PetThumb pet={pet} className="size-12" />
        <p className="min-w-0 text-sm text-bark">
          {pet.weightKg ? `${pet.weightKg} kg` : "Chưa có cân nặng"}
        </p>
      </div>

      {pet.allergies && (
        <p className="mb-4 flex gap-2 rounded-[var(--radius-control)] border border-amber/40 bg-amber/10 p-2.5 text-sm text-ink">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-amber" />
          <span>
            <span className="font-medium">Dị ứng:</span> {pet.allergies}
          </span>
        </p>
      )}

      {records.isLoading ? (
        <Spinner label="Đang tải lịch sử khám" />
      ) : records.isError ? (
        <ErrorState
          message={records.error instanceof ApiError ? records.error.message : "Không tải được bệnh án."}
          onRetry={() => records.refetch()}
        />
      ) : (records.data ?? []).length === 0 ? (
        <EmptyState title="Chưa có bệnh án nào" description="Bé này chưa có lần khám nào được ghi lại." />
      ) : (
        <MedicalRecordList
          records={records.data as MedicalRecord[]}
          currentAppointmentId={currentAppointmentId}
          openInNewTab
        />
      )}
    </Dialog>
  );
}
