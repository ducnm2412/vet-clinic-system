"use client";

import { useQuery } from "@tanstack/react-query";
import { Stethoscope } from "lucide-react";
import { ApiError, doctorApi } from "@/lib/api";
import { PageHeader } from "@/components/layout/DashboardShell";
import { EmptyState, ErrorState, Skeleton, Tag } from "@/components/ui";

/**
 * Danh sách bác sĩ. Dùng `GET /profile/doctors` — endpoint công khai dành cho trang đặt
 * lịch, nên chỉ có thông tin chuyên môn, không có email hay chứng chỉ.
 *
 * Chứng chỉ hành nghề chỉ chính bác sĩ xem được (`/profile/doctor/me/licenses`), quản trị
 * không có đường nào lấy. Không dựng cột trống cho nó ở đây.
 */
export default function AdminDoctorsPage() {
  const doctors = useQuery({ queryKey: ["doctors"], queryFn: doctorApi.listPublic });

  return (
    <>
      <PageHeader title="Bác sĩ" description="Đội ngũ bác sĩ đang làm việc tại phòng khám." />

      {doctors.isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
        </div>
      ) : doctors.isError ? (
        <ErrorState
          message={
            doctors.error instanceof ApiError ? doctors.error.message : "Không tải được danh sách."
          }
          onRetry={() => doctors.refetch()}
        />
      ) : (doctors.data ?? []).length === 0 ? (
        <div className="rounded-[var(--radius-control)] border border-line bg-surface">
          <EmptyState
            title="Chưa có bác sĩ nào"
            description="Tài khoản bác sĩ tạo ở mục Tài khoản; hồ sơ chuyên môn do chính bác sĩ điền."
          />
        </div>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {(doctors.data ?? []).map((d) => (
            <li
              key={d.id}
              className="rounded-[var(--radius-control)] border border-line bg-surface p-4"
            >
              <div className="flex items-start gap-3">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-moss-wash">
                  <Stethoscope aria-hidden className="size-4 text-moss" />
                </span>
                <div className="min-w-0 flex-1">
                  <h2 className="font-medium text-ink">{d.specialty || "Chưa ghi chuyên môn"}</h2>
                  {d.yearsOfExperience != null && (
                    <Tag className="mt-1">{d.yearsOfExperience} năm kinh nghiệm</Tag>
                  )}
                </div>
              </div>
              {d.bio && <p className="mt-3 line-clamp-3 text-sm text-ink-soft">{d.bio}</p>}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
