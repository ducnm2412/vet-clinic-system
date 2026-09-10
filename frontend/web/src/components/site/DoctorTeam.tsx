"use client";

import { useQuery } from "@tanstack/react-query";
import { Stethoscope } from "lucide-react";
import { doctorApi } from "@/lib/api";
import type { DoctorPublic } from "@/types";
import { ButtonLink, Section, SectionHead } from "./primitives";

/**
 * Đội ngũ bác sĩ, lấy từ GET /profile/doctors (công khai, không cần đăng nhập).
 *
 * TODO(backend, VD-20): `DoctorPublicResponse` chỉ trả specialty, bio và yearsOfExperience
 * — KHÔNG có họ tên và không có ảnh. Nên thẻ dưới đây lấy chuyên môn làm tiêu đề.
 * Không lấy ảnh người lạ gán tên bác sĩ của phòng khám: đó là dựng chuyện.
 * Cần bổ sung: GET /profile/doctors trả thêm fullName và photoUrl.
 */
export function DoctorTeam() {
  const doctors = useQuery({ queryKey: ["doctors", "public"], queryFn: doctorApi.listPublic });

  const list = doctors.data ?? [];

  // Trang giới thiệu không phải chỗ bày lỗi kỹ thuật. Chưa có bác sĩ nào khai hồ sơ, hoặc
  // gọi hỏng, thì bỏ hẳn dải này đi còn hơn để một khoảng trống có chữ "không tải được".
  if (doctors.isLoading) return <DoctorSkeleton />;
  if (doctors.isError || list.length === 0) return null;

  return (
    <Section tone="white">
      <SectionHead
        title="Bác sĩ phụ trách"
        lede="Bạn không chọn bác sĩ khi đặt lịch — hệ thống xếp người đang rảnh vào khung giờ bạn chọn. Ai cũng khai chứng chỉ hành nghề trong hệ thống."
        action={
          <ButtonLink href="/appointments/create" variant="outline">
            Đặt lịch khám
          </ButtonLink>
        }
      />

      <ul className="grid gap-x-8 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
        {list.slice(0, 6).map((d) => (
          <DoctorCard key={d.id} doctor={d} />
        ))}
      </ul>
    </Section>
  );
}

function DoctorCard({ doctor }: { doctor: DoctorPublic }) {
  const years = doctor.yearsOfExperience;

  return (
    <li className="flex gap-5">
      {/*
        Không có ảnh chân dung nên đây là mái vòm mòng két kèm ống nghe — cùng mô-típ với
        mọi khung ảnh khác của trang, nên trông vẫn là một phần của thiết kế.
      */}
      <span
        aria-hidden
        className="arch-sm grid h-24 w-18 shrink-0 place-items-center bg-pine text-white/80"
      >
        <Stethoscope className="size-7" />
      </span>

      <div className="min-w-0">
        <h3 className="t-h3">{doctor.specialty?.trim() || "Bác sĩ thú y"}</h3>
        {years != null && years > 0 && (
          <p className="tnum mt-1 text-sm text-teal-deep">{years} năm kinh nghiệm</p>
        )}
        <p className="mt-2 text-stone">
          {doctor.bio?.trim() || "Khám và điều trị cho chó, mèo và thú cưng nhỏ tại phòng khám."}
        </p>
      </div>
    </li>
  );
}

function DoctorSkeleton() {
  return (
    <Section tone="white">
      <div className="mb-12 h-10 w-72 animate-pulse rounded-full bg-mist" />
      <ul className="grid gap-x-8 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
        {[0, 1, 2].map((i) => (
          <li key={i} className="flex gap-5">
            <span className="arch-sm h-24 w-18 shrink-0 animate-pulse bg-mist" />
            <div className="min-w-0 flex-1 space-y-2 pt-2">
              <div className="h-5 w-3/4 animate-pulse rounded bg-mist" />
              <div className="h-4 w-full animate-pulse rounded bg-mist" />
              <div className="h-4 w-2/3 animate-pulse rounded bg-mist" />
            </div>
          </li>
        ))}
      </ul>
    </Section>
  );
}
