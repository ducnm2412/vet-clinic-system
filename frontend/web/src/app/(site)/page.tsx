"use client";

/*
  Trang chủ (giao diện chuyển từ trang dịch vụ cũ) dựng theo ảnh mẫu (skill image-to-ui): hero cam nhạt + hotline, dải "Đồ cho bé",
  thanh đặt lịch nhanh xanh rêu, khối giới thiệu phòng khám, lưới 4 dịch vụ khám, banner giờ
  mở cửa cam/xanh. Dịch vụ lấy từ booking-service, sản phẩm từ product-service. Ảnh từng dịch
  vụ là ảnh có sẵn trong /public/img (backend không lưu ảnh dịch vụ).
*/

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  CalendarDays,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  Heart,
  MapPin,
  PawPrint,
  Phone,
  Play,
  ShieldCheck,
  Stethoscope,
  Award,
  HeartHandshake,
  CheckCircle2,
} from "lucide-react";
import { clinicServiceApi, productApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { CLINIC } from "@/config/clinic";
import { Leaf, Paw } from "@/components/site/Decor";
import { ProductImage } from "@/components/site/ProductCard";
import { CareBanner } from "@/components/site/CareBanner";

const ORANGE = "#f57c1f";
const GREEN = "#1f4a3c";
const SERVICE_IMAGES = ["/img/meo.jpg", "/img/cho-con.jpg", "/img/hero.jpg", "/img/cham-soc.jpg"];
const BLOBS = ["#d7f0ea", "#ffe7d2", "#ffe0d6", "#d9efe9", "#ffe9c9"];

export default function HomePage() {
  return (
    <div className="bg-white">
      <div className="mx-auto max-w-[1440px] space-y-10 px-4 pb-0 pt-4 sm:px-6">
        <Hero />
        <KidStuff />
        <QuickBooking />
        <About />
        <ServiceGrid />
        <AboutCompany />
      </div>
      <OpenHours />
    </div>
  );
}

/* ---------- Hero ---------- */

const SLIDES = [
  {
    badge: "Phòng khám thú y cho thú cưng",
    title: "Chăm sóc tận tâm",
    accent: "cho thú cưng",
    desc: "Bác sĩ có chuyên môn, tận tình, trang thiết bị hiện đại, đồng hành cùng thú cưng của bạn.",
    cta: { href: "/appointments/create", label: "Đặt lịch khám" },
    image: "/img/dv-hero.png",
    alt: "Chú chó golden và mèo con",
  },
  {
    badge: "Cấp cứu 24/7",
    title: "Luôn có bác sĩ trực",
    accent: "cả đêm lẫn lễ Tết",
    desc: "Phòng cấp cứu sẵn oxy, máy truyền dịch và thuốc chống sốc. Gọi trước để đội trực chuẩn bị.",
    cta: { href: "/services/cap-cuu-24-7", label: "Xem dịch vụ cấp cứu" },
    image: "/img/cham-soc.jpg",
    alt: "Hai chú chó chạy trên đường",
  },
  {
    badge: "Tiêm phòng – tẩy giun",
    title: "Phòng bệnh sớm",
    accent: "để bé khoẻ cả năm",
    desc: "Vắc-xin chính hãng, sổ tiêm điện tử và nhắc lịch tự động cho từng mũi tiêm.",
    cta: { href: "/services/tiem-phong", label: "Đặt lịch tiêm phòng" },
    image: "/img/cho-con.jpg",
    alt: "Chú chó khoẻ mạnh",
  },
  {
    badge: "Cửa hàng thú cưng",
    title: "Đồ dùng cho bé",
    accent: "chọn tại phòng khám",
    desc: "Thức ăn, phụ kiện và đồ chăm sóc chính hãng, được bác sĩ tư vấn đúng nhu cầu của bé.",
    cta: { href: "/products", label: "Mua sắm ngay" },
    image: "/img/banner-shop.png",
    alt: "Chú chó corgi và mèo con",
  },
] as const;

const SLIDE_MS = 5000;
const IMG_MASK = "radial-gradient(ellipse 62% 70% at 50% 58%, #000 60%, transparent 100%)";

function Hero() {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const go = (i: number) => setIndex((i + SLIDES.length) % SLIDES.length);

  // Tự chuyển slide; dừng khi rê chuột / focus vào hero. Đổi slide tay thì đếm lại từ đầu.
  useEffect(() => {
    if (paused) return;
    const t = setTimeout(() => setIndex((i) => (i + 1) % SLIDES.length), SLIDE_MS);
    return () => clearTimeout(t);
  }, [index, paused]);

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Giới thiệu nổi bật"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
      className="relative overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_0%_0%,#fffaf4_0%,#ffeedd_45%,#ffd9b3_100%)] md:min-h-[460px]"
    >
      {/* Mảng cam CSS phía sau ảnh */}
      <div aria-hidden className="absolute -bottom-24 right-[4%] hidden aspect-square h-[115%] rounded-full bg-[radial-gradient(circle,#ffc48f_0%,#f7ad62_55%,transparent_72%)] md:block" />

      {/* Ảnh các slide xếp chồng, chỉ slide hiện tại hiện ra (mờ dần + thu phóng nhẹ) */}
      {SLIDES.map((sl, i) => (
        <div
          key={sl.image}
          aria-hidden={i !== index}
          className={`absolute bottom-0 right-[6%] hidden aspect-[460/338] h-[92%] transition-all duration-700 ease-out motion-reduce:transition-none md:block ${
            i === index ? "scale-100 opacity-100" : "scale-[1.04] opacity-0"
          }`}
          style={{ maskImage: IMG_MASK, WebkitMaskImage: IMG_MASK }}
        >
          <Image src={sl.image} alt={sl.alt} fill priority={i === 0} unoptimized className="object-cover" />
        </div>
      ))}

      <Leaf className="bottom-10 right-3 hidden h-24 w-10 rotate-[30deg] bg-[linear-gradient(135deg,#7cc36b,#3f8f3a)] md:block" />
      <Leaf className="bottom-24 right-10 hidden h-16 w-7 rotate-[60deg] bg-[linear-gradient(135deg,#8fd07c,#4b9a45)] md:block" />
      <Paw className="right-6 top-[38%] hidden size-10 rotate-12 text-white md:block" />
      <Paw className="left-[44%] top-10 size-9 rotate-[-15deg] text-[#f9a55c]" />
      <Paw className="left-[42%] bottom-16 hidden size-7 rotate-12 text-[#f9a55c] md:block" />

      <a
        href={`tel:${CLINIC.phone.replace(/\s/g, "")}`}
        className="absolute right-5 top-5 z-10 hidden items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-xl md:flex"
      >
        <span className="grid size-10 place-items-center rounded-full bg-[#fff1e4]" style={{ color: GREEN }}>
          <Phone className="size-5" />
        </span>
        <span className="leading-tight">
          <span className="block text-xs" style={{ color: GREEN }}>Hotline</span>
          <span className="block text-sm font-bold text-[#2b1d14]">{CLINIC.phone}</span>
        </span>
      </a>

      <div className="relative z-10 px-6 py-10 sm:px-12 md:max-w-[50%] md:py-12">
        {/* Chữ các slide xếp chồng trong cùng một ô lưới để chiều cao không nhảy */}
        <div className="grid">
          {SLIDES.map((sl, i) => {
            const on = i === index;
            return (
              <div
                key={sl.title}
                aria-hidden={!on}
                inert={!on}
                className={`[grid-area:1/1] transition-all duration-700 ease-out motion-reduce:transition-none ${
                  on ? "translate-y-0 opacity-100" : "pointer-events-none translate-y-4 opacity-0"
                }`}
              >
                <span className="inline-block rounded-full bg-[#ffe1c7] px-4 py-1.5 text-sm font-medium text-[#3b2a20]">{sl.badge}</span>
                {i === 0 ? (
                  <h1 className="mt-5 text-[38px] font-extrabold leading-[1.1] sm:text-[48px]" style={{ color: GREEN }}>
                    {sl.title}
                    <br />
                    <span style={{ color: ORANGE }}>{sl.accent}</span>
                  </h1>
                ) : (
                  <p className="mt-5 text-[38px] font-extrabold leading-[1.1] sm:text-[48px]" style={{ color: GREEN }}>
                    {sl.title}
                    <br />
                    <span style={{ color: ORANGE }}>{sl.accent}</span>
                  </p>
                )}
                <p className="mt-4 max-w-md text-[15px] text-[#2b1d14]">{sl.desc}</p>
                <div className="mt-7 flex flex-wrap gap-3">
                  <Link
                    href={sl.cta.href}
                    className="inline-flex h-12 items-center gap-2 rounded-full px-6 text-sm font-semibold text-white shadow-[0_8px_18px_-8px_rgba(245,124,31,0.9)]"
                    style={{ background: ORANGE }}
                  >
                    <CalendarDays className="size-4" /> {sl.cta.label} <ArrowRight className="size-4" />
                  </Link>
                  <a
                    href="#dich-vu-kham"
                    className="inline-flex h-12 items-center gap-2 rounded-full border-2 bg-white/60 px-6 text-sm font-semibold"
                    style={{ borderColor: GREEN, color: GREEN }}
                  >
                    <Play className="size-4 fill-current" /> Xem dịch vụ
                  </a>
                </div>
              </div>
            );
          })}
        </div>

        <dl className="mt-8 flex flex-wrap gap-6">
          {[
            { v: "7/7", l: "Hỗ trợ cả tuần", icon: PawPrint },
            { v: "1 phút", l: "Đặt lịch nhanh", icon: ShieldCheck },
            { v: "100%", l: "Bác sĩ có chứng chỉ", icon: Heart },
          ].map(({ v, l, icon: Icon }) => (
            <div key={l} className="flex items-center gap-2.5">
              <span className="grid size-10 place-items-center rounded-full bg-[#ffe1c7]" style={{ color: ORANGE }}>
                <Icon className="size-5 fill-current" />
              </span>
              <div className="leading-tight">
                <dt className="font-bold text-[#2b1d14]">{v}</dt>
                <dd className="text-xs text-[#4a3a30]">{l}</dd>
              </div>
            </div>
          ))}
        </dl>

        {/* Điều khiển slide */}
        <div className="mt-8 flex items-center gap-3">
          <button
            onClick={() => go(index - 1)}
            aria-label="Slide trước"
            className="grid size-10 place-items-center rounded-full border border-[#f3d5b5] bg-white/80 text-[#2b1d14] transition hover:border-[#f57c1f] hover:text-[#f57c1f]"
          >
            <ChevronLeft className="size-5" />
          </button>
          <div className="flex items-center gap-2" role="tablist" aria-label="Chọn slide">
            {SLIDES.map((sl, i) => (
              <button
                key={sl.title}
                role="tab"
                aria-selected={i === index}
                aria-label={`Slide ${i + 1}: ${sl.badge}`}
                onClick={() => go(i)}
                className="h-2.5 rounded-full transition-all duration-300"
                style={{ width: i === index ? 32 : 10, background: i === index ? ORANGE : "#f3c9a1" }}
              />
            ))}
          </div>
          <button
            onClick={() => go(index + 1)}
            aria-label="Slide sau"
            className="grid size-10 place-items-center rounded-full border border-[#f3d5b5] bg-white/80 text-[#2b1d14] transition hover:border-[#f57c1f] hover:text-[#f57c1f]"
          >
            <ChevronRight className="size-5" />
          </button>
        </div>
      </div>
    </section>
  );
}

/* ---------- Đồ cho bé ---------- */

function SectionTitle({ title, action }: { title: string; action: React.ReactNode }) {
  return (
    <div className="mb-5 flex items-center gap-4">
      <PawPrint className="size-7 shrink-0 fill-current" style={{ color: ORANGE }} />
      <h2 className="shrink-0 text-2xl font-extrabold" style={{ color: GREEN }}>{title}</h2>
      <span className="h-px flex-1 bg-[#e8e2dc]" />
      {action}
    </div>
  );
}

function KidStuff() {
  const products = useQuery({
    queryKey: ["products", { size: 5, sort: "createdAt,desc" }],
    queryFn: () => productApi.search({ size: 5, sort: "createdAt,desc" }),
  });
  return (
    <section>
      <SectionTitle
        title="Đồ cho bé"
        action={
          <Link href="/products" className="inline-flex h-8 items-center gap-1 rounded-full px-3 text-xs font-semibold text-white" style={{ background: GREEN }}>
            Tất cả sản phẩm <ArrowRight className="size-3.5" />
          </Link>
        }
      />
      <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        {(products.data?.content ?? []).map((p, i) => (
          <li key={p.id} className="flex flex-col items-center rounded-3xl bg-white p-5 text-center shadow-[0_4px_24px_-10px_rgba(0,0,0,0.15)]">
            <span className="grid size-24 place-items-center overflow-hidden rounded-full" style={{ background: BLOBS[i % BLOBS.length] }}>
              <ProductImage product={p} className="size-20 !rounded-full !bg-transparent [&_img]:object-contain" />
            </span>
            <p className="mt-4 line-clamp-2 min-h-[2.5rem] text-sm text-[#2b1d14]">{p.name}</p>
            <p className="mt-1 text-sm font-semibold" style={{ color: ORANGE }}>{formatPrice(p.price)}</p>
            <Link href={`/products/${p.id}`} className="mt-3 inline-flex h-7 items-center gap-1 rounded-full bg-[#f3f6f4] px-3 text-xs" style={{ color: GREEN }}>
              Xem chi tiết <ArrowRight className="size-3" />
            </Link>
          </li>
        ))}
        {products.isLoading &&
          Array.from({ length: 5 }).map((_, i) => <li key={i} className="h-56 animate-pulse rounded-3xl bg-[#fff3e8]" />)}
      </ul>
    </section>
  );
}

/* ---------- Đặt lịch nhanh ---------- */

function QuickBooking() {
  return (
    <section className="relative">
      <Paw className="-left-2 top-1/2 hidden size-8 -translate-y-1/2 -rotate-12 text-[#f9a55c] lg:block" />
      <div className="mx-auto flex max-w-[1300px] flex-col gap-4 rounded-[28px] px-5 py-4 text-white lg:flex-row lg:items-center" style={{ background: `linear-gradient(90deg, ${GREEN} 0%, ${GREEN} 60%, #ffe4cc 100%)` }}>
        <div className="flex flex-1 items-center gap-4">
          <span className="grid size-12 shrink-0 place-items-center rounded-xl bg-white" style={{ color: ORANGE }}>
            <CalendarDays className="size-6" />
          </span>
          <div>
            <p className="text-lg font-bold">Đặt lịch khám nhanh</p>
            <p className="text-sm text-white/80">Tiết kiệm thời gian, chăm sóc thú cưng tốt hơn</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-3">
          <span className="inline-flex h-11 items-center gap-2 rounded-xl bg-white px-4 text-sm font-medium text-[#2b1d14]">
            <Clock className="size-5" style={{ color: ORANGE }} /> 7:30 - 20:00 <ChevronDown className="size-4" />
          </span>
          <span className="inline-flex h-11 items-center gap-2 rounded-xl bg-white px-4 text-sm font-medium text-[#2b1d14]">
            <MapPin className="size-5" style={{ color: ORANGE }} /> Cần Thơ <ChevronDown className="size-4" />
          </span>
        </div>
        <Link
          href="/appointments/create"
          className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff8a2a] to-[#f2661b] px-10 text-base font-bold text-white shadow-[0_8px_18px_-8px_rgba(245,124,31,0.9)]"
        >
          Đặt lịch online <ArrowRight className="size-4" />
        </Link>
      </div>
    </section>
  );
}

/* ---------- Giới thiệu ---------- */

function About() {
  return (
    <section className="grid items-center gap-6 overflow-hidden rounded-[28px] bg-gradient-to-r from-[#fff6ee] to-[#ffe9d6] md:grid-cols-[230px_1fr_auto]">
      <div className="relative h-48 md:h-full md:min-h-[150px]">
        <Image src="/img/cho-con.jpg" alt="Chú chó tại phòng khám" fill sizes="230px" className="object-cover md:rounded-r-[28px]" />
      </div>
      <div className="px-6 md:px-0 md:py-6">
        <h2 className="text-2xl font-extrabold" style={{ color: ORANGE }}>Phòng khám hiện đại, tận tâm</h2>
        <p className="mt-3 max-w-xl text-sm leading-relaxed text-[#3b2a20]">
          Đội ngũ bác sĩ thú y giàu kinh nghiệm, trang thiết bị hiện đại, mang đến dịch vụ chăm sóc sức khoẻ tốt nhất cho thú cưng của bạn.
        </p>
        <Link href="/" className="mt-3 inline-flex items-center gap-1 text-sm font-semibold" style={{ color: ORANGE }}>
          Đọc thêm <ArrowRight className="size-4" />
        </Link>
      </div>
      <div className="grid grid-cols-2 gap-4 px-6 pb-6 md:mr-6 md:px-0 md:pb-0">
        {[
          { icon: PawPrint, t: "Bác sĩ có chuyên môn và tận tâm" },
          { icon: ShieldCheck, t: "Môi trường sạch sẽ hiện đại" },
        ].map(({ icon: Icon, t }) => (
          <div key={t} className="flex w-40 flex-col items-center rounded-2xl bg-white/70 px-3 py-4 text-center">
            <span className="grid size-14 place-items-center rounded-full bg-white shadow" style={{ color: ORANGE }}>
              <Icon className="size-7 fill-current" />
            </span>
            <p className="mt-3 text-xs text-[#3b2a20]">{t}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

/* ---------- Dịch vụ khám ---------- */

function ServiceGrid() {
  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });
  const list = (services.data ?? []).filter((s) => s.active).sort((a, b) => a.displayOrder - b.displayOrder).slice(0, 4);
  return (
    <section id="dich-vu-kham" className="scroll-mt-24">
      <div className="mb-5 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <PawPrint className="size-7 fill-current" style={{ color: ORANGE }} />
          <h2 className="text-2xl font-extrabold text-[#2b1d14]">Dịch vụ khám</h2>
        </div>
        <Link href="/services" className="inline-flex h-8 items-center gap-1 rounded-full px-3 text-xs font-semibold text-white" style={{ background: ORANGE }}>
          Tất cả dịch vụ <ArrowRight className="size-3.5" />
        </Link>
      </div>
      <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {list.map((s, i) => (
          <li key={s.id}>
            <Link
              href={`/services/${s.slug}`}
              className="group block rounded-3xl bg-white p-2.5 shadow-[0_4px_24px_-10px_rgba(0,0,0,0.18)] transition hover:-translate-y-0.5"
            >
              <div className="relative aspect-[16/9] overflow-hidden rounded-2xl">
                <Image src={SERVICE_IMAGES[i % SERVICE_IMAGES.length]} alt="" fill sizes="(max-width:1024px) 50vw, 25vw" className="object-cover transition duration-500 group-hover:scale-105" />
              </div>
              <div className="flex items-center gap-2 px-3 py-3">
                <div className="flex-1 text-center">
                  <p className="font-medium text-[#2b1d14]">{s.name}</p>
                  {s.referencePrice != null && (
                    <p className="text-sm" style={{ color: ORANGE }}>Từ {formatPrice(s.referencePrice)}</p>
                  )}
                </div>
                <span className="grid size-8 place-items-center rounded-full bg-[#f4f1ee] text-[#2b1d14] transition group-hover:bg-[#f57c1f] group-hover:text-white">
                  <ArrowRight className="size-4" />
                </span>
              </div>
            </Link>
          </li>
        ))}
        {services.isLoading &&
          Array.from({ length: 4 }).map((_, i) => <li key={i} className="h-56 animate-pulse rounded-3xl bg-[#fff3e8]" />)}
      </ul>
    </section>
  );
}

/* ---------- Giới thiệu công ty ---------- */

const MILESTONES = [
  { value: "8+", label: "Năm kinh nghiệm" },
  { value: "15.000+", label: "Thú cưng được chăm sóc" },
  { value: "12", label: "Bác sĩ & kỹ thuật viên" },
  { value: "98%", label: "Khách hàng quay lại" },
];

const VALUES = [
  { icon: Stethoscope, title: "Chuyên môn vững", body: "Bác sĩ được đào tạo bài bản, cập nhật phác đồ điều trị mới mỗi năm." },
  { icon: HeartHandshake, title: "Tận tâm như người nhà", body: "Mỗi bé được theo dõi bằng bệnh án riêng, chủ nuôi luôn được giải thích rõ ràng." },
  { icon: Award, title: "Minh bạch chi phí", body: "Báo giá trước khi điều trị, thuốc và vật tư có nguồn gốc rõ ràng." },
];

function AboutCompany() {
  return (
    <section className="relative overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_100%_0%,#fffaf4_0%,#ffeedd_45%,#ffd9b3_100%)] px-6 py-12 sm:px-12 md:py-16">
      <Paw className="right-[8%] top-8 size-9 rotate-12 text-[#f9a55c]" />
      <Paw className="bottom-10 left-[46%] hidden size-7 -rotate-12 text-[#fbb983] md:block" />
      <Heart aria-hidden className="absolute right-[4%] bottom-14 hidden size-12 md:block" strokeWidth={1.75} style={{ color: ORANGE }} />

      <div className="relative grid items-center gap-12 lg:grid-cols-[1fr_1.1fr]">
        {/* Cụm ảnh */}
        <div className="relative mx-auto w-full max-w-lg">
          <div aria-hidden className="absolute -inset-6 rounded-[58%_42%_55%_45%/48%_60%_40%_52%] bg-[linear-gradient(135deg,#ffcf9f,#f7a65a)] opacity-70" />
          <div className="relative grid grid-cols-[1.3fr_1fr] gap-4">
            <div className="relative row-span-2 overflow-hidden rounded-[28px] shadow-xl">
              <Image src="/img/cham-soc.jpg" alt="Thú cưng vui vẻ tại phòng khám" fill sizes="(max-width:1024px) 60vw, 25vw" className="object-cover" />
            </div>
            <div className="relative aspect-square overflow-hidden rounded-[24px] shadow-lg">
              <Image src="/img/meo.jpg" alt="" fill sizes="20vw" className="object-cover" />
            </div>
            <div className="relative aspect-square overflow-hidden rounded-[24px] shadow-lg">
              <Image src="/img/cho-con.jpg" alt="" fill sizes="20vw" className="object-cover" />
            </div>
          </div>
          <div className="absolute -bottom-6 left-6 flex items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-xl">
            <span className="grid size-11 place-items-center rounded-full text-white" style={{ background: ORANGE }}>
              <Award className="size-6" />
            </span>
            <div className="leading-tight">
              <p className="text-lg font-extrabold" style={{ color: GREEN }}>Từ 2018</p>
              <p className="text-xs text-[#6b5a4e]">Đồng hành cùng thú cưng Cần Thơ</p>
            </div>
          </div>
        </div>

        {/* Nội dung */}
        <div>
          <span className="inline-flex items-center gap-2 rounded-full bg-[#ffe1c7] px-4 py-1.5 text-sm font-medium text-[#3b2a20]">
            <PawPrint className="size-4" style={{ color: ORANGE }} /> Về chúng tôi
          </span>
          <h2 className="mt-5 text-[32px] font-extrabold leading-[1.15] sm:text-[40px]" style={{ color: GREEN }}>
            Vet Clinic – ngôi nhà thứ hai <span style={{ color: ORANGE }}>của thú cưng</span>
          </h2>
          <p className="mt-4 text-[15px] leading-relaxed text-[#3b2a20]">
            Bắt đầu từ một phòng khám nhỏ năm 2018, Vet Clinic nay là hệ thống chăm sóc thú cưng với đầy đủ khám
            chữa bệnh, xét nghiệm, phẫu thuật, spa và lưu trú. Chúng tôi tin rằng mỗi chú chó, chú mèo đều xứng
            đáng được chăm sóc bằng chuyên môn tốt nhất và sự yêu thương chân thành.
          </p>

          <ul className="mt-6 space-y-4">
            {VALUES.map(({ icon: Icon, title, body }) => (
              <li key={title} className="flex gap-4">
                <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-white shadow" style={{ color: ORANGE }}>
                  <Icon className="size-5" />
                </span>
                <div>
                  <p className="font-bold text-[#2b1d14]">{title}</p>
                  <p className="text-sm text-[#6b5a4e]">{body}</p>
                </div>
              </li>
            ))}
          </ul>

          <dl className="mt-8 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {MILESTONES.map((m) => (
              <div key={m.label} className="rounded-2xl bg-white/80 px-3 py-4 text-center shadow-sm">
                <dt className="text-2xl font-extrabold" style={{ color: ORANGE }}>{m.value}</dt>
                <dd className="mt-1 text-xs text-[#6b5a4e]">{m.label}</dd>
              </div>
            ))}
          </dl>

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <Link
              href="/appointments/create"
              className="inline-flex h-12 items-center gap-2 rounded-full px-6 text-sm font-semibold text-white shadow-[0_8px_18px_-8px_rgba(245,124,31,0.9)]"
              style={{ background: ORANGE }}
            >
              Đặt lịch khám <ArrowRight className="size-4" />
            </Link>
            <p className="flex items-center gap-2 text-sm text-[#3b2a20]">
              <CheckCircle2 className="size-5" style={{ color: GREEN }} /> {CLINIC.address}
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ---------- Banner cuối (giống trang sản phẩm) ---------- */

function OpenHours() {
  return (
    <div className="mt-12">
      <CareBanner
        title="Hôm nay bé thế nào?"
        subtitle="Đội ngũ bác sĩ luôn sẵn sàng tư vấn và hỗ trợ chăm sóc thú cưng của bạn!"
        extra={
          <p className="mt-4 inline-flex items-center gap-2 rounded-full bg-white/80 px-4 py-1.5 text-sm font-semibold" style={{ color: ORANGE }}>
            <Clock className="size-4" /> 7:30 - 20:00 · Mở cửa tất cả các ngày
          </p>
        }
        cta={{ href: "/appointments/create", label: "Đặt lịch ngay" }}
        image={{ src: "/img/dv-banner.png", alt: "Chú chó đang cười", width: 308, height: 163, displayWidth: 480 }}
      />
    </div>
  );
}
