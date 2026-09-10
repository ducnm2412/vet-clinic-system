import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { ChevronLeft } from "lucide-react";
import { BrandLockup } from "@/components/site/Brand";

/**
 * Khung của đăng nhập, đăng ký và xác minh email. Từ khi có website khách hàng, đây là
 * cửa vào chính của người ngoài — nên nó mang nhận diện của mặt tiền, không phải của
 * dashboard: mòng két, mái vòm, ảnh thú cưng.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh bg-white text-pine lg:grid-cols-[1fr_30rem]">
      <section className="relative hidden flex-col justify-between overflow-hidden bg-pine p-10 text-white lg:flex">
        <BrandLockup tone="light" className="relative z-10" />

        <div className="relative z-10 max-w-md">
          <h2 className="t-h2">Hồ sơ của bé, luôn ở trong túi bạn.</h2>
          <p className="mt-5 text-white/70">
            Đăng nhập để đặt lịch khám, xem chẩn đoán của bác sĩ và theo dõi đơn hàng — cùng
            một tài khoản.
          </p>
        </div>

        {/* Vòm ảnh chồm ra khỏi mép dưới, cùng mô-típ với trang chủ. */}
        <div
          aria-hidden
          className="arch pointer-events-none absolute -bottom-24 -right-16 z-0 h-96 w-72 opacity-90"
        >
          <Image
            src="/img/meo.jpg"
            alt=""
            fill
            sizes="18rem"
            className="object-cover"
          />
        </div>
      </section>

      <section className="flex flex-col px-6 py-8 sm:px-10">
        <div className="flex items-center justify-between lg:justify-end">
          <span className="lg:hidden">
            <BrandLockup />
          </span>
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-[15px] text-stone hover:text-pine"
          >
            <ChevronLeft aria-hidden className="size-4" />
            Về trang chủ
          </Link>
        </div>

        <div className="flex flex-1 items-center justify-center py-10">
          <div className="w-full max-w-sm">{children}</div>
        </div>
      </section>
    </div>
  );
}
