import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { ChevronLeft, Headphones, Heart, Phone, ShieldCheck } from "lucide-react";
import { BrandLockup } from "@/components/site/Brand";
import { Paw } from "@/components/site/Decor";
import { CLINIC } from "@/config/clinic";

/**
 * Khung của đăng nhập, đăng ký và xác minh email: nửa trái xanh rêu có lời chào + ảnh mèo trong vòm,
 * nửa phải nền kem rắc dấu chân, form nằm trong thẻ bo góc. Tông cam/xanh giống trang chủ.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.08fr_1fr]">
      {/* ---------- Nửa trái ---------- */}
      <section className="relative hidden flex-col overflow-hidden bg-[#134a42] px-12 py-14 text-white lg:flex">
        <Paw className="left-[4%] top-[24%] size-14 rotate-[-20deg] text-white/10" />
        <Paw className="right-[16%] top-[16%] size-12 rotate-12 text-white/10" />
        <Paw className="right-[24%] top-[22%] size-8 text-white/10" />
        <Paw className="right-[6%] top-[48%] size-14 -rotate-12 text-white/10" />
        <Paw className="bottom-[22%] left-[3%] size-14 rotate-12 text-white/10" />
        <Heart aria-hidden className="absolute left-[50%] top-[33%] size-8 text-[#f28a3c]/60" />
        <Heart aria-hidden className="absolute right-[6%] top-[60%] size-11 rotate-12 text-[#f28a3c]" />
        <Heart aria-hidden className="absolute bottom-[24%] left-[54%] size-6 fill-[#f28a3c] text-[#f28a3c]" />
        <Heart aria-hidden className="absolute bottom-[10%] left-[56%] size-10 fill-[#f5a04f] text-[#f5a04f]" />

        <BrandLockup tone="light" className="relative z-10" />

        <div className="relative z-10 mt-auto mb-auto max-w-xl pt-20">
          <h2 className="text-[46px] font-extrabold leading-[1.12] xl:text-[56px]">
            Hồ sơ của bé, luôn ở trong túi bạn.
          </h2>
          <p className="mt-6 max-w-lg text-[17px] font-medium leading-relaxed text-white/90">
            Đăng nhập để đặt lịch khám, xem chẩn đoán của bác sĩ và theo dõi đơn hàng – cùng một
            tài khoản.
          </p>
        </div>

        <a
          href={`tel:${CLINIC.phone.replace(/\s/g, "")}`}
          className="relative z-10 inline-flex w-fit items-center gap-3 rounded-full bg-white/10 px-6 py-3 text-[17px] font-semibold text-[#f5a04f]"
        >
          <Phone className="size-5 fill-current" /> Hotline: {CLINIC.phone}
        </a>

        {/* Ảnh mèo trong vòm xanh, chồm ra mép dưới. */}
        <div
          aria-hidden
          className="pointer-events-none absolute -bottom-6 right-[4%] h-[23rem] w-[19rem] overflow-hidden rounded-t-[999px] bg-[#3f9c78]"
        >
          <Image src="/img/meo.jpg" alt="" fill sizes="19rem" className="object-cover" />
        </div>
      </section>

      {/* ---------- Nửa phải ---------- */}
      <section className="relative flex flex-col overflow-hidden bg-[#fdf0e2] px-5 py-6 sm:px-10">
        <Paw className="left-[5%] top-[7%] size-16 -rotate-12 text-[#f7b77c]" />
        <Paw className="left-[44%] top-[11%] size-11 rotate-12 text-[#f9cfa6]" />
        <Paw className="right-[3%] top-[12%] size-12 rotate-12 text-[#f9cfa6]" />
        <Paw className="right-[2%] top-[32%] size-8 text-[#f7b77c]" />
        <Paw className="left-[2%] top-[42%] size-12 -rotate-12 text-[#f9cfa6]" />
        <Paw className="right-[3%] top-[56%] size-8 rotate-12 text-[#f7b77c]" />
        <Paw className="bottom-[14%] left-[7%] size-14 rotate-12 text-[#f9cfa6]" />
        <Paw className="bottom-[12%] right-[5%] size-14 -rotate-12 text-[#f9cfa6]" />

        <div className="relative z-10 flex items-center justify-between lg:justify-end">
          <span className="lg:hidden">
            <BrandLockup />
          </span>
          <Link href="/" className="inline-flex items-center gap-1 text-[17px] text-[#1f4a3c] hover:text-[#f26b1d]">
            <ChevronLeft aria-hidden className="size-4" />
            Về trang chủ
          </Link>
        </div>

        <div className="relative z-10 flex flex-1 items-center justify-center py-6">
          <div
            className={[
              "w-full max-w-[34rem] rounded-[28px] bg-[#fffaf3] px-6 py-8 shadow-[0_18px_50px_-24px_rgba(160,90,30,0.45)] sm:px-8",
              // Ô nhập, nhãn và nút trong thẻ theo tông cam/xanh.
              "[&_label]:text-[17px] [&_label]:font-semibold [&_label]:text-[#1f4a3c]",
              "[&_label_span]:text-[#f26b1d]",
              "[&_input]:h-12 [&_input]:rounded-xl [&_input]:border-[#ead9c5] [&_input]:bg-[#fffaf3] [&_input]:text-[16px]",
              "[&_input:focus]:border-[#f26b1d]",
              "[&_button[type=submit]]:!h-14 [&_button[type=submit]]:!rounded-xl [&_button[type=submit]]:!bg-[#f26b1d] [&_button[type=submit]]:!text-[19px] [&_button[type=submit]]:!text-white [&_button[type=submit]:hover]:!bg-[#e05a10]",
              "[&_h1]:font-[family-name:var(--font-brand)] [&_h1]:text-[40px] [&_h1]:font-bold [&_h1]:leading-tight [&_h1]:text-[#1f4a3c]",
              "[&_h1+p]:text-[18px] [&_h1+p]:text-[#2b1d14]",
              "[&_h1+p_a]:font-semibold [&_h1+p_a]:text-[#f26b1d] [&_h1+p_a]:decoration-[#f26b1d]",
            ].join(" ")}
          >
            {children}
          </div>
        </div>

        <p className="relative z-10 flex items-center justify-center gap-2 text-[15px] font-semibold text-[#1f4a3c] sm:justify-end">
          Bảo mật dữ liệu • Hỗ trợ 24/7
          <ShieldCheck className="size-5" />
          <Headphones className="size-5" />
        </p>
      </section>
    </div>
  );
}
