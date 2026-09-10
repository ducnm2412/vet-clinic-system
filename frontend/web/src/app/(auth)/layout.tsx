import Link from "next/link";
import type { ReactNode } from "react";

/**
 * Trang đăng nhập và đăng ký dùng chung khung này. Cột trái là hình ảnh nhận diện,
 * cột phải là biểu mẫu — trên điện thoại chỉ còn biểu mẫu.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1fr_28rem]">
      <section className="hidden flex-col justify-between bg-moss p-10 text-white lg:flex">
        <Link href="/" className="text-[15px] font-semibold tracking-tight">
          Thú y Vet Clinic
        </Link>

        <div className="max-w-md">
          <h2 className="font-[family-name:var(--font-display)] text-[31px] leading-[1.2]">
            Hồ sơ khám, kho thuốc và đơn hàng nằm chung một chỗ
          </h2>
          <p className="mt-4 text-[15px] leading-relaxed text-white/75">
            Bác sĩ mở bệnh án ngay trên ca đang khám. Nhân viên thu tiền thuốc và theo dõi tồn
            kho mà không phải đổi phần mềm.
          </p>
        </div>

        <p className="text-sm text-white/60">Phòng khám thú y</p>
      </section>

      <section className="flex items-center justify-center bg-surface px-6 py-10">
        <div className="w-full max-w-sm">{children}</div>
      </section>
    </div>
  );
}
