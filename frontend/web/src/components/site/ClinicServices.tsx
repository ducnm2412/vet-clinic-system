"use client";

import { useQuery } from "@tanstack/react-query";
import { HeartPulse, Scissors, ShieldCheck, Stethoscope, Syringe, type LucideIcon } from "lucide-react";
import { clinicServiceApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { Section, SectionHead } from "./primitives";

/**
 * VD-21: danh mục dịch vụ lấy từ `GET /booking/services` thay vì nội dung tĩnh trong code.
 * Quản trị thêm hay ngừng dịch vụ là trang này đổi theo, không phải build lại frontend.
 *
 * Biểu tượng vẫn nằm ở frontend và tra theo `slug`: ảnh minh hoạ là việc của giao diện, không
 * đáng để backend phải lưu. Dịch vụ mới chưa có biểu tượng riêng thì dùng ống nghe.
 */
const ICONS: Record<string, LucideIcon> = {
  "kham-tong-quat": Stethoscope,
  "tiem-phong": ShieldCheck,
  "phau-thuat": HeartPulse,
  "cham-soc-lam-dep": Scissors,
  "sieu-am": Syringe,
};

export function ClinicServices() {
  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });

  const list = services.data ?? [];

  // Trang giới thiệu không phải chỗ bày lỗi kỹ thuật — giống DoctorTeam, hỏng thì bỏ hẳn dải
  // này còn hơn để một khoảng trống có chữ "không tải được".
  if (services.isLoading) return <ServicesSkeleton />;
  if (services.isError || list.length === 0) return null;

  return (
    <Section id="dich-vu" tone="white">
      <SectionHead
        title="Bé cần gì, phòng khám làm được gì"
        lede="Những việc chúng tôi làm hằng ngày. Ca phức tạp hơn thì bác sĩ hội chẩn rồi báo bạn hướng xử lý."
      />

      <ul className="grid gap-x-14 sm:grid-cols-2">
        {list.map((s, i) => {
          const Icon = ICONS[s.slug] ?? Stethoscope;
          return (
            <li
              key={s.id}
              className={`flex gap-5 border-t border-mist py-8 ${
                i === 0 ? "border-t-0" : ""
              } ${i === 1 ? "sm:border-t-0" : ""}`}
            >
              <span
                aria-hidden
                className="arch-sm grid size-14 shrink-0 place-items-center bg-mint text-teal"
              >
                <Icon className="size-6" />
              </span>
              <div className="min-w-0">
                <h3 className="t-h3">{s.name}</h3>
                {s.description && <p className="mt-2 text-stone">{s.description}</p>}
                <p className="mt-2 text-[15px] text-stone">
                  Khoảng {s.durationMinutes} phút
                  {s.referencePrice !== null && ` · từ ${formatPrice(s.referencePrice)}`}
                </p>
              </div>
            </li>
          );
        })}
      </ul>
    </Section>
  );
}

function ServicesSkeleton() {
  return (
    <Section id="dich-vu" tone="white">
      <div className="grid gap-x-14 sm:grid-cols-2" aria-hidden>
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="flex gap-5 py-8">
            <div className="arch-sm size-14 shrink-0 animate-pulse bg-mint" />
            <div className="min-w-0 flex-1 space-y-3">
              <div className="h-6 w-1/3 animate-pulse rounded bg-mint" />
              <div className="h-4 w-full animate-pulse rounded bg-mint" />
              <div className="h-4 w-2/3 animate-pulse rounded bg-mint" />
            </div>
          </div>
        ))}
      </div>
    </Section>
  );
}
