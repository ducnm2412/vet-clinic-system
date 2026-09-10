"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, staffApi } from "@/lib/api";
import type { StaffProfile } from "@/types";
import { PageHeader } from "@/components/layout/DashboardShell";
import { Button, ErrorState, Skeleton, TextField, useToast } from "@/components/ui";

export default function StaffProfilePage() {
  const profile = useQuery({ queryKey: ["staff", "me"], queryFn: staffApi.me });

  if (profile.isLoading) return <Skeleton className="h-64 max-w-md" />;
  if (profile.isError || !profile.data) {
    return <ErrorState message="Không tải được hồ sơ." onRetry={() => profile.refetch()} />;
  }

  return (
    <>
      <PageHeader title="Hồ sơ của tôi" description="Thông tin nhân sự của bạn tại phòng khám." />
      {/*
        Biểu mẫu chỉ dựng khi đã có dữ liệu, và `key` gắn theo id để nếu hồ sơ đổi thì
        React dựng lại component với giá trị mới — không cần effect đồng bộ state.
      */}
      <ProfileForm key={profile.data.id} profile={profile.data} />
    </>
  );
}

function ProfileForm({ profile }: { profile: StaffProfile }) {
  const qc = useQueryClient();
  const toast = useToast();

  const [position, setPosition] = useState(profile.position ?? "");
  const [phone, setPhone] = useState(profile.phone ?? "");
  const [hireDate, setHireDate] = useState(profile.hireDate ?? "");

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

  return (
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
  );
}
