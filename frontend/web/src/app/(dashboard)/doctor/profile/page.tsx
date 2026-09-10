"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import { ApiError, doctorApi } from "@/lib/api";
import { formatDate } from "@/lib/utils/format";
import { PageHeader } from "@/components/layout/DashboardShell";
import {
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  IconButton,
  Skeleton,
  TextAreaField,
  TextField,
  useToast,
} from "@/components/ui";

export default function DoctorProfilePage() {
  const qc = useQueryClient();
  const toast = useToast();

  const profile = useQuery({ queryKey: ["doctor", "me"], queryFn: doctorApi.me });
  const licenses = useQuery({ queryKey: ["doctor", "licenses"], queryFn: doctorApi.licenses });

  const [specialty, setSpecialty] = useState("");
  const [phone, setPhone] = useState("");
  const [bio, setBio] = useState("");
  const [years, setYears] = useState("");
  const [addOpen, setAddOpen] = useState(false);

  useEffect(() => {
    const d = profile.data;
    if (!d) return;
    setSpecialty(d.specialty ?? "");
    setPhone(d.phone ?? "");
    setBio(d.bio ?? "");
    setYears(d.yearsOfExperience != null ? String(d.yearsOfExperience) : "");
  }, [profile.data]);

  const save = useMutation({
    mutationFn: () =>
      doctorApi.updateMe({
        specialty: specialty || undefined,
        phone: phone || undefined,
        bio: bio || undefined,
        yearsOfExperience: years === "" ? undefined : Number(years),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["doctor", "me"] });
      qc.invalidateQueries({ queryKey: ["doctors"] });
      toast.success("Đã lưu hồ sơ");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được hồ sơ."),
  });

  const removeLicense = useMutation({
    mutationFn: (id: string) => doctorApi.deleteLicense(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["doctor", "licenses"] });
      toast.success("Đã xoá chứng chỉ");
    },
  });

  if (profile.isLoading) return <Skeleton className="h-64" />;
  if (profile.isError) {
    return <ErrorState message="Không tải được hồ sơ." onRetry={() => profile.refetch()} />;
  }

  return (
    <>
      <PageHeader
        title="Hồ sơ của tôi"
        description="Thông tin này hiện trên trang tra cứu bác sĩ công khai."
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <section className="rounded-[var(--radius-control)] border border-line bg-surface p-4">
          <h2 className="mb-3 font-medium text-ink">Thông tin chuyên môn</h2>
          <div className="space-y-4">
            <TextField
              label="Chuyên môn"
              placeholder="Nội khoa thú nhỏ"
              value={specialty}
              onChange={(e) => setSpecialty(e.target.value)}
            />
            <div className="grid gap-3 sm:grid-cols-2">
              <TextField
                label="Điện thoại"
                inputMode="numeric"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
              <TextField
                label="Số năm kinh nghiệm"
                type="number"
                min={0}
                value={years}
                onChange={(e) => setYears(e.target.value)}
              />
            </div>
            <TextAreaField
              label="Giới thiệu"
              rows={4}
              hint="Vài dòng để chủ nuôi biết bạn khám mảng gì"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
            />
            <Button loading={save.isPending} onClick={() => save.mutate()}>
              Lưu hồ sơ
            </Button>
          </div>
        </section>

        <section className="rounded-[var(--radius-control)] border border-line bg-surface">
          <div className="flex items-center gap-3 border-b border-line px-4 py-3">
            <h2 className="font-medium text-ink">Chứng chỉ hành nghề</h2>
            <Button size="sm" variant="secondary" className="ml-auto" onClick={() => setAddOpen(true)}>
              <Plus aria-hidden className="size-4" />
              Thêm
            </Button>
          </div>

          {licenses.isLoading ? (
            <div className="p-4">
              <Skeleton className="h-16" />
            </div>
          ) : (licenses.data ?? []).length === 0 ? (
            <EmptyState title="Chưa khai chứng chỉ nào" />
          ) : (
            <ul className="divide-y divide-line">
              {(licenses.data ?? []).map((l) => (
                <li key={l.id} className="flex items-start gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-ink">{l.licenseNumber}</p>
                    <p className="text-sm text-bark">
                      {l.issuedBy || "Chưa ghi nơi cấp"}
                      {l.expiryDate ? ` · hết hạn ${formatDate(l.expiryDate)}` : ""}
                    </p>
                  </div>
                  <IconButton
                    label={`Xoá chứng chỉ ${l.licenseNumber}`}
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      if (confirm(`Xoá chứng chỉ ${l.licenseNumber}?`)) removeLicense.mutate(l.id);
                    }}
                  >
                    <Trash2 aria-hidden className="size-4" />
                  </IconButton>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <AddLicenseDialog open={addOpen} onClose={() => setAddOpen(false)} />
    </>
  );
}

function AddLicenseDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [licenseNumber, setLicenseNumber] = useState("");
  const [issuedBy, setIssuedBy] = useState("");
  const [issuedDate, setIssuedDate] = useState("");
  const [expiryDate, setExpiryDate] = useState("");

  const add = useMutation({
    mutationFn: () =>
      doctorApi.addLicense({
        licenseNumber,
        issuedBy: issuedBy || undefined,
        issuedDate: issuedDate || undefined,
        expiryDate: expiryDate || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["doctor", "licenses"] });
      toast.success("Đã thêm chứng chỉ");
      setLicenseNumber("");
      setIssuedBy("");
      setIssuedDate("");
      setExpiryDate("");
      onClose();
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không thêm được."),
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Thêm chứng chỉ hành nghề"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Huỷ
          </Button>
          <Button
            disabled={licenseNumber.trim() === ""}
            loading={add.isPending}
            onClick={() => add.mutate()}
          >
            Thêm chứng chỉ
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <TextField
          label="Số hiệu"
          required
          value={licenseNumber}
          onChange={(e) => setLicenseNumber(e.target.value)}
        />
        <TextField label="Nơi cấp" value={issuedBy} onChange={(e) => setIssuedBy(e.target.value)} />
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField
            label="Ngày cấp"
            type="date"
            value={issuedDate}
            onChange={(e) => setIssuedDate(e.target.value)}
          />
          <TextField
            label="Hết hạn"
            type="date"
            value={expiryDate}
            onChange={(e) => setExpiryDate(e.target.value)}
          />
        </div>
      </div>
    </Dialog>
  );
}
