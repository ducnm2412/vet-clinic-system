"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, staffApi } from "@/lib/api";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Button, ErrorState, Skeleton, TextField, useToast } from "@/components/ui";

export default function StaffProfilePage() {
  const qc = useQueryClient();
  const toast = useToast();

  const profile = useQuery({ queryKey: ["staff", "me"], queryFn: staffApi.me });

  const [position, setPosition] = useState("");
  const [phone, setPhone] = useState("");
  const [hireDate, setHireDate] = useState("");

  useEffect(() => {
    const d = profile.data;
    if (!d) return;
    setPosition(d.position ?? "");
    setPhone(d.phone ?? "");
    setHireDate(d.hireDate ?? "");
  }, [profile.data]);

  const save = useMutation({
    mutationFn: () =>
      staffApi.updateMe({
        position: position || undefined,
        phone: phone || undefined,
        hireDate: hireDate || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["staff"] });
      toast.success("Đã lưu hồ sơ");
    },
    onError: (err) => toast.error(err instanceof ApiError ? err.message : "Không lưu được hồ sơ."),
  });

  if (profile.isLoading) return <Skeleton className="h-64 max-w-md" />;
  if (profile.isError) {
    return <ErrorState message="Không tải được hồ sơ." onRetry={() => profile.refetch()} />;
  }

  return (
    <>
      <PageHeader title="Hồ sơ của tôi" description="Thông tin nhân sự của bạn tại phòng khám." />

      <section className="max-w-md space-y-4 rounded-[var(--radius-control)] border border-line bg-surface p-4">
        <TextField
          label="Vị trí"
          placeholder="Lễ tân, thủ kho…"
          value={position}
          onChange={(e) => setPosition(e.target.value)}
        />
        <TextField
          label="Điện thoại"
          inputMode="numeric"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <TextField
          label="Ngày vào làm"
          type="date"
          value={hireDate}
          onChange={(e) => setHireDate(e.target.value)}
        />
        <Button loading={save.isPending} onClick={() => save.mutate()}>
          Lưu hồ sơ
        </Button>
      </section>
    </>
  );
}
