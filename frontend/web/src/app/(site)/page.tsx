"use client";

/*
  Trang chủ dựng lại theo ảnh mẫu (skill image-to-ui): hero xanh ngọc tràn ngang, dải 5 sản
  phẩm có nền "đốm", hai banner đôi, khối cam + trắng, lưới 4 thẻ, banner cam/xanh cuối trang.
  Chữ trong ảnh mẫu là chữ vô nghĩa, nên nội dung lấy từ dữ liệu thật: sản phẩm từ
  product-service, dịch vụ từ booking-service, thông tin phòng khám từ config/clinic.
*/

import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, PawPrint, Phone } from "lucide-react";
import { clinicServiceApi, productApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { CLINIC } from "@/config/clinic";
import type { Product } from "@/types";

const C = {
  hero: "#5b9c91",
  dark: "#3e6e65",
  light: "#82c3b8",
  orange: "#ec8a5c",
  cream: "#f7f5f0",
};

export default function HomePage() {
  return (
    <div style={{ background: C.cream }}>
      <Hero />
      <div className="mx-auto max-w-6xl px-4 pb-16 sm:px-6">
        <ProductStrip />
        <TwinBanners />
        <AboutBlock />
        <ServiceGrid />
        <ClosingBanner />
      </div>
    </div>
  );
}

/* ---------- Hero ---------- */

function Hero() {
  return (
    <section className="relative">
      <div style={{ background: C.hero }}>
        <div className="mx-auto grid max-w-6xl items-end gap-6 px-4 pt-14 sm:px-6 md:grid-cols-[1fr_1.15fr] md:pt-20">
          <div className="pb-10 md:pb-28">
            <h1 className="font-[family-name:var(--font-brand)] text-[44px] font-bold leading-[1.05] text-white sm:text-[58px]">
              Chăm sóc tận tâm
              <br />
              <span style={{ color: "#f6b48a" }}>cho thú cưng</span>
            </h1>
            <p className="mt-5 max-w-sm text-[15px] text-white/85">
              Phòng khám thú y cho chó, mèo. Bác sĩ có chứng chỉ hành nghề, đặt lịch online trong một phút.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link
                href="/appointments/create"
                className="inline-flex h-10 items-center rounded-full px-6 text-sm font-medium text-white"
                style={{ background: C.dark }}
              >
                Đặt lịch khám
              </Link>
              <Link
                href="/products"
                className="inline-flex h-10 items-center gap-2 rounded-full border-2 border-white/90 px-5 text-sm font-medium text-white"
              >
                <PawPrint className="size-4" aria-hidden />
                Xem sản phẩm
              </Link>
            </div>
          </div>

          <div className="relative mx-auto aspect-[5/4] w-full max-w-xl md:-mb-10">
            <Image
              src="/img/hero.jpg"
              alt="Chú chó ngồi nhìn thẳng vào ống kính"
              fill
              priority
              sizes="(max-width: 768px) 92vw, 50vw"
              className="rounded-t-[28px] object-cover"
            />
          </div>
        </div>
      </div>
      {/* Sàn trắng dưới hero + chấm trình chiếu như ảnh mẫu */}
      <div className="h-14 bg-white shadow-[0_10px_20px_-18px_rgba(0,0,0,0.25)]" />
      <div className="absolute inset-x-0 bottom-4 flex justify-center gap-2" aria-hidden>
        {[0, 1, 2, 3].map((i) => (
          <span key={i} className="size-2.5 rounded-full" style={{ background: i === 0 ? C.dark : "#4a4a4a" }} />
        ))}
      </div>
    </section>
  );
}

/* ---------- Dải sản phẩm ---------- */

const BLOBS = ["#bfe3dd", "#cfe9e4", "#fde3cf", "#d5ece8", "#fbdcd3"];

function useProducts(count: number) {
  return useQuery({
    queryKey: ["products", { size: 12 }],
    queryFn: () => productApi.search({ size: 12 }),
    select: (d) =>
      [...d.content].sort((a, b) => Number(Boolean(b.imageUrl)) - Number(Boolean(a.imageUrl))).slice(0, count),
  });
}

function SectionTitle({ title, href, label }: { title: string; href: string; label: string }) {
  return (
    <div className="mb-6 flex items-center gap-4">
      <h2 className="font-[family-name:var(--font-brand)] text-[26px] font-bold text-[#2d3b38]">{title}</h2>
      <span className="h-px flex-1 bg-[#2d3b38]/40" />
      <Link
        href={href}
        className="inline-flex h-7 items-center gap-1 rounded px-3 text-xs font-medium text-white"
        style={{ background: C.dark }}
      >
        {label}
        <ArrowRight className="size-3" aria-hidden />
      </Link>
    </div>
  );
}

function ProductStrip() {
  const products = useProducts(5);
  const list = products.data ?? [];

  return (
    <section className="pt-12">
      <SectionTitle title="Đồ cho bé" href="/products" label="Tất cả" />
      <ul className="grid grid-cols-2 gap-px overflow-hidden rounded-lg bg-[#e7e3dc] shadow-[0_6px_20px_-10px_rgba(0,0,0,0.25)] sm:grid-cols-3 lg:grid-cols-5">
        {(products.isLoading ? Array.from({ length: 5 }) : list).map((p, i) => (
          <li key={(p as Product | undefined)?.id ?? i} className="bg-white px-4 pb-5 pt-6 text-center">
            {p ? <StripItem product={p as Product} index={i} /> : <div className="h-52 animate-pulse rounded bg-[#f1eee8]" />}
          </li>
        ))}
      </ul>
    </section>
  );
}

function StripItem({ product, index }: { product: Product; index: number }) {
  const orange = index % 2 === 1;
  return (
    <Link href={`/products/${product.id}`} className="group block">
      <div
        className="relative mx-auto grid size-28 place-items-center rounded-[42%_58%_55%_45%/50%_45%_55%_50%]"
        style={{ background: BLOBS[index % BLOBS.length] }}
      >
        {product.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={product.imageUrl} alt="" className="size-20 rounded-lg object-cover" />
        ) : (
          <PawPrint className="size-10" style={{ color: C.dark }} aria-hidden />
        )}
        <span className="absolute -right-1 top-1 grid size-6 place-items-center rounded-full bg-white/80 text-[11px]" style={{ color: C.dark }}>
          +
        </span>
      </div>
      <p className="mt-4 line-clamp-1 text-sm font-medium text-[#2d3b38]">{product.name}</p>
      <p className="mt-1 text-sm" style={{ color: C.orange }}>
        {formatPrice(product.price)}
      </p>
      <span
        className="mt-3 inline-flex h-7 items-center rounded-full px-4 text-xs font-medium text-white transition-opacity group-hover:opacity-90"
        style={{ background: orange ? C.orange : C.dark }}
      >
        Xem chi tiết
      </span>
    </Link>
  );
}

/* ---------- Hai banner ---------- */

function TwinBanners() {
  return (
    <section className="mt-10 grid gap-4 rounded-xl bg-white p-3 shadow-[0_6px_20px_-10px_rgba(0,0,0,0.25)] md:grid-cols-2">
      <MiniBanner bg={C.dark} img="/img/cho-con.jpg" label="Khám tổng quát" text={CLINIC.hours.split(",")[1]?.trim() ?? CLINIC.hours} href="/appointments/create" />
      <MiniBanner bg={C.light} img="/img/meo.jpg" label="Tiêm phòng" text="Đặt lịch online" href="/appointments/create" />
    </section>
  );
}

function MiniBanner({ bg, img, label, text, href }: { bg: string; img: string; label: string; text: string; href: string }) {
  return (
    <Link href={href} className="relative flex h-16 items-center justify-between overflow-hidden rounded-lg px-6 text-white" style={{ background: bg }}>
      <span className="text-sm">{label}</span>
      <span className="relative h-full w-20 overflow-hidden">
        <Image src={img} alt="" fill sizes="80px" className="object-cover" />
      </span>
      <span className="font-[family-name:var(--font-brand)] text-lg font-semibold">{text}</span>
    </Link>
  );
}

/* ---------- Khối cam + trắng ---------- */

function AboutBlock() {
  return (
    <section className="mt-6 grid overflow-hidden rounded-lg shadow-[0_6px_20px_-10px_rgba(0,0,0,0.25)] md:grid-cols-2">
      <div className="flex items-center gap-5 p-5" style={{ background: C.orange }}>
        <div className="relative aspect-[4/3] w-36 shrink-0 overflow-hidden rounded-md">
          <Image src="/img/cham-soc.jpg" alt="Bàn tay vuốt ve một chú mèo" fill sizes="144px" className="object-cover" />
        </div>
        <div className="text-white">
          <h3 className="font-[family-name:var(--font-brand)] text-lg font-bold">Một phòng khám nhỏ, làm kỹ</h3>
          <p className="mt-1 text-sm text-white/90">
            Bác sĩ giải thích cho bạn hiểu bé đang bị gì trước khi kê đơn. Nhận số ca vừa phải để mỗi ca có đủ thời gian.
          </p>
          <Link href="/appointments/create" className="mt-2 inline-flex items-center gap-1 text-xs font-semibold uppercase tracking-wide">
            Đặt lịch <ArrowRight className="size-3" aria-hidden />
          </Link>
        </div>
      </div>
      <div className="grid grid-cols-[auto_1fr_1fr] items-center gap-5 bg-white p-5">
        <div className="relative aspect-square w-24 overflow-hidden rounded-full">
          <Image src="/img/meo.jpg" alt="Chú mèo con" fill sizes="96px" className="object-cover" />
        </div>
        <InfoCol title="Bác sĩ có chứng chỉ" body="Mỗi lần khám đều lưu bệnh án" href="/appointments/create" cta="Đặt lịch" />
        <InfoCol title="Mở cửa cả tuần" body="7:30 – 20:00, kể cả Chủ Nhật" href={`tel:${CLINIC.phone.replace(/\s/g, "")}`} cta="Gọi ngay" />
      </div>
    </section>
  );
}

function InfoCol({ title, body, href, cta }: { title: string; body: string; href: string; cta: string }) {
  return (
    <div>
      <p className="font-[family-name:var(--font-brand)] font-semibold text-[#2d3b38]">{title}</p>
      <p className="mt-1 text-xs text-[#5c6f6b]">{body}</p>
      <a href={href} className="mt-3 inline-flex h-7 items-center rounded px-3 text-xs font-medium text-white" style={{ background: C.orange }}>
        {cta}
      </a>
    </div>
  );
}

/* ---------- Lưới 4 thẻ (dịch vụ) ---------- */

const CARD_IMAGES = ["/img/meo.jpg", "/img/cho-con.jpg", "/img/hero.jpg", "/img/cham-soc.jpg"];

function ServiceGrid() {
  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });
  const list = (services.data ?? []).slice(0, 4);
  if (services.isError || (!services.isLoading && list.length === 0)) return null;

  return (
    <section className="mt-12">
      <div className="mb-6 flex items-center justify-between">
        <h2 className="font-[family-name:var(--font-brand)] text-[26px] font-bold text-[#2d3b38]">Dịch vụ khám</h2>
        <Link href="/appointments/create" className="inline-flex h-7 items-center rounded px-4 text-xs font-medium text-white" style={{ background: C.orange }}>
          Đặt lịch ngay
        </Link>
      </div>
      <ul className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {(services.isLoading ? Array.from({ length: 4 }) : list).map((s, i) => (
          <li key={i} className="rounded-md bg-white p-3 shadow-[0_6px_20px_-10px_rgba(0,0,0,0.25)]">
            {s ? (
              <>
                <div className="flex items-center justify-between">
                  <span
                    className="rounded px-2 py-0.5 text-[11px] font-medium text-white"
                    style={{ background: i % 2 ? C.dark : C.orange }}
                  >
                    {(s as { durationMinutes: number }).durationMinutes} phút
                  </span>
                  <span className="h-2 w-8 rounded-full" style={{ background: C.dark }} aria-hidden />
                </div>
                <div className="relative mt-3 aspect-[4/3] overflow-hidden rounded bg-[#f1eee8]">
                  <Image src={CARD_IMAGES[i]} alt="" fill sizes="(max-width: 1024px) 45vw, 22vw" className="object-cover" />
                </div>
                <p className="mt-3 text-center text-sm font-medium text-[#2d3b38]">{(s as { name: string }).name}</p>
                <p className="mt-1 text-center text-sm" style={{ color: C.orange }}>
                  {(s as { referencePrice: number | null }).referencePrice != null
                    ? `Từ ${formatPrice((s as { referencePrice: number }).referencePrice)}`
                    : "Báo giá khi khám"}
                </p>
              </>
            ) : (
              <div className="aspect-[3/4] animate-pulse rounded bg-[#f1eee8]" />
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

/* ---------- Banner cuối ---------- */

function ClosingBanner() {
  return (
    <section className="relative mt-12 grid overflow-hidden rounded-lg md:grid-cols-2">
      <div className="relative px-8 py-10 text-white" style={{ background: C.orange }}>
        <p className="font-[family-name:var(--font-brand)] text-4xl font-bold">7:30 – 20:00</p>
        <p className="mt-1 text-sm text-white/90">Mở cửa cả Chủ Nhật</p>
        <a
          href={`tel:${CLINIC.phone.replace(/\s/g, "")}`}
          className="mt-6 inline-flex h-8 items-center gap-2 rounded-full px-4 text-xs font-medium text-white"
          style={{ background: C.dark }}
        >
          <Phone className="size-3.5" aria-hidden />
          {CLINIC.phone}
        </a>
        <span className="absolute bottom-6 right-28 hidden size-12 place-items-center rounded-full bg-white/25 md:grid" aria-hidden>
          <PawPrint className="size-6" />
        </span>
      </div>
      <div className="px-8 py-10 text-white md:pl-36" style={{ background: C.dark }}>
        <p className="font-[family-name:var(--font-brand)] text-3xl font-bold" style={{ color: C.light }}>
          Hôm nay bé thế nào?
        </p>
        <p className="mt-2 max-w-xs text-sm text-white/85">
          Bé có gì khác thường thì đừng đợi xem sao. Đặt một lịch khám, mất vài phút.
        </p>
        <Link
          href="/appointments/create"
          className="mt-5 inline-flex h-8 items-center rounded-full px-5 text-xs font-medium text-white"
          style={{ background: C.orange }}
        >
          Đặt lịch khám
        </Link>
      </div>
      {/* Chú chó con ở giữa, như ảnh mẫu */}
      <div className="pointer-events-none absolute bottom-0 left-1/2 hidden h-[115%] w-56 -translate-x-1/2 md:block">
        <Image src="/img/cho-con.jpg" alt="" fill sizes="224px" className="rounded-t-full object-cover" />
      </div>
      <span
        className="absolute left-[calc(50%+6.25rem)] top-1/2 hidden size-14 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full md:grid"
        style={{ background: C.orange }}
        aria-hidden
      >
        <PawPrint className="size-7 text-white" />
      </span>
    </section>
  );
}
