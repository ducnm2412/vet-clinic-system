"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { doctorApi } from "@/lib/api";
import type { DoctorPublic } from "@/types";

/**
 * Tra bác sĩ theo userId để hiện lên lịch hẹn.
 *
 * Lịch hẹn giờ trả `doctorUserId` (VD-16), còn thông tin bác sĩ nằm ở GET /profile/doctors —
 * endpoint công khai nên khách, lễ tân và quản trị đều gọi được, không phải xin thêm quyền.
 *
 * Nhãn ưu tiên họ tên (VD-20). Hồ sơ lập trước khi backend lưu tên thì rơi về chuyên môn,
 * để không bao giờ hiện một ô trống.
 */
export function useDoctorDirectory() {
  const doctors = useQuery({
    queryKey: ["doctors", "public"],
    queryFn: doctorApi.listPublic,
    // Danh sách bác sĩ gần như không đổi trong một phiên làm việc.
    staleTime: 5 * 60_000,
  });

  const byUserId = useMemo(
    () => new Map((doctors.data ?? []).map((d) => [d.userId, d])),
    [doctors.data],
  );

  return {
    ...doctors,
    byUserId,
    /** Nhãn ngắn gọn cho một bác sĩ; không bao giờ trả chuỗi rỗng. */
    label: (userId: string | null | undefined) => doctorLabel(userId ? byUserId.get(userId) : undefined, doctors.isLoading),
  };
}

export function doctorLabel(doctor: DoctorPublic | undefined, loading = false): string {
  if (loading) return "Đang tải…";
  // Không có trong danh sách: hồ sơ bác sĩ chưa lập, hoặc tài khoản đã bị xoá sau khi khám.
  if (!doctor) return "Bác sĩ phòng khám";
  const name = doctor.fullName?.trim();
  if (name) return `BS. ${name}`;
  const specialty = doctor.specialty?.trim();
  return specialty ? `Bác sĩ ${specialty.charAt(0).toLowerCase()}${specialty.slice(1)}` : "Bác sĩ thú y";
}
