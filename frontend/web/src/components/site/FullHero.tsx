"use client";

/*
  Hero toàn màn hình theo bố cục mẫu: slider ảnh tràn viền, tiêu đề trái + nút đặt lịch, mũi tên hai bên,
  3 thẻ dịch vụ đè lên mép dưới (thẻ giữa màu cam). Ảnh: Unsplash (giấy phép Unsplash, dùng tự do).

  Motion (skill motion-design, tính cách Playful):
  - Chuyển slide: ảnh mới mở ra theo hình tròn từ phía phải (clip-path, 900ms) + Ken Burns chậm (ambient).
  - Tiêu đề: từng chữ trồi lên khỏi mặt nạ, mỗi chữ trễ 50ms (tổng < 500ms); nút đến sau cùng.
  - Dấu chân hiện lần lượt như đang bước đi (ambient, lặp).
  - Blob cam nền lượn chậm; thanh tiến trình chạy theo thời gian slide.
  - Thẻ dịch vụ: trồi lên lần lượt; hover nhấc thẻ, nền cam quét từ dưới lên, icon lắc nhẹ.
*/

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { ArrowRight, CalendarDays, ChevronLeft, ChevronRight, Scissors, Smile, Syringe, type LucideIcon } from "lucide-react";

const SLIDES = [
  { image: "/img/hero/slide-1.jpg", pos: "50% 30%", line1: "Không chỉ là thú cưng,", line2: "bé là người thân", alt: "Chú chó và mèo nằm chơi trên cỏ" },
  { image: "/img/hero/slide-2.jpg", pos: "70% 35%", line1: "Bác sĩ tận tâm,", line2: "bé khoẻ mỗi ngày", alt: "Chú chó Australian Shepherd cười trên bãi biển" },
  { image: "/img/hero/slide-3.jpg", pos: "35% 40%", line1: "Tiêm phòng đúng lịch,", line2: "lớn lên an toàn", alt: "Chú chó golden con đeo vòng cổ đỏ" },
];
const MS = 6500;

const CARDS: { icon: LucideIcon; title: string; body: string; href: string; featured?: boolean }[] = [
  { icon: Smile, title: "Chăm sóc răng miệng", body: "Cạo vôi, vệ sinh răng, giữ nướu khoẻ mạnh.", href: "/services/rang-mieng" },
  { icon: Syringe, title: "Tiêm phòng", body: "Phòng bệnh sớm, nhắc lịch tự động từng mũi.", href: "/services/tiem-phong", featured: true },
  { icon: Scissors, title: "Triệt sản & phẫu thuật", body: "Phòng mổ vô trùng, theo dõi đến khi bé tỉnh.", href: "/services/phau-thuat" },
];

export function FullHero() {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const go = (i: number) => setIndex((i + SLIDES.length) % SLIDES.length);

  useEffect(() => {
    if (paused) return;
    const t = setTimeout(() => setIndex((i) => (i + 1) % SLIDES.length), MS);
    return () => clearTimeout(t);
  }, [index, paused]);

  const s = SLIDES[index];

  return (
    <section className="relative isolate overflow-hidden bg-[#fbe3cf] pb-10">
      {/* Blob cam nền (lớp ambient) */}
      <div aria-hidden className="blob-a absolute -left-40 top-[55%] -z-10 size-[520px] bg-[#f6c9a3]" />
      <div aria-hidden className="blob-b absolute -right-32 -top-40 -z-10 size-[620px] bg-[#f4c29a]" />

      <div
        className="relative"
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}
      >
        {/* Khung ảnh */}
        <div
          aria-roledescription="carousel"
          aria-label="Ảnh nổi bật"
          className="relative h-svh min-h-[560px] overflow-hidden bg-[#1f4a3c]"
        >
          {SLIDES.map((sl, i) => (
            <div key={sl.image} aria-hidden={i !== index} className={`absolute inset-0 ${i === index ? "slide-in z-10" : "z-0"}`}>
              <Image
                src={sl.image}
                alt={sl.alt}
                fill
                priority={i === 0}
                sizes="100vw"
                className={`object-cover ${i === index ? "ken-burns" : ""}`}
                style={{ objectPosition: sl.pos }}
              />
            </div>
          ))}
          <div aria-hidden className="absolute inset-0 z-20 bg-[linear-gradient(90deg,rgba(20,30,25,0.78)_0%,rgba(20,30,25,0.45)_40%,rgba(20,30,25,0)_70%)]" />
          <div aria-hidden className="absolute inset-x-0 bottom-0 z-20 h-1/3 bg-gradient-to-t from-black/45 to-transparent" />

          <PawTrail />

          {/* Nội dung */}
          <div className="absolute inset-0 z-30 flex items-center px-6 pb-24 sm:px-16 lg:pl-[max(7rem,calc((100vw-1400px)/2))]">
            <div key={index} className="max-w-4xl text-white">
              <h1 className="text-[36px] font-extrabold leading-[1.12] sm:text-[50px] xl:text-[60px]">
                <Words text={s.line1} start={0} />
                <br />
                <Words text={s.line2} start={s.line1.split(" ").length} className="text-[#ffb36b]" />
              </h1>
              <div className="word-in mt-8" style={{ animationDelay: "520ms" }}>
                <Link
                  href="/appointments/create"
                  className="press shine group relative inline-flex h-14 items-center gap-2 overflow-hidden rounded-xl bg-[#f57c1f] px-7 text-base font-bold text-white shadow-[0_12px_28px_-10px_rgba(245,124,31,0.95)]"
                >
                  <CalendarDays className="size-5" /> Đặt lịch khám <ArrowRight className="nudge size-5" />
                </Link>
              </div>
            </div>
          </div>

          {/* Mũi tên */}
          <button
            onClick={() => go(index - 1)}
            aria-label="Ảnh trước"
            className="press absolute left-3 top-1/2 z-40 hidden size-12 sm:grid -translate-y-1/2 place-items-center rounded-full bg-white/15 text-white backdrop-blur transition-colors hover:bg-white hover:text-[#f57c1f] sm:left-5"
          >
            <ChevronLeft className="size-6" />
          </button>
          <button
            onClick={() => go(index + 1)}
            aria-label="Ảnh sau"
            className="press absolute right-3 top-1/2 z-40 hidden size-12 sm:grid -translate-y-1/2 place-items-center rounded-full bg-white/15 text-white backdrop-blur transition-colors hover:bg-white hover:text-[#f57c1f] sm:right-5"
          >
            <ChevronRight className="size-6" />
          </button>

          {/* Thanh tiến trình từng slide */}
          <div className="absolute right-6 top-6 z-40 flex gap-2 sm:right-10" role="tablist" aria-label="Chọn ảnh">
            {SLIDES.map((sl, i) => (
              <button
                key={sl.image}
                role="tab"
                aria-selected={i === index}
                aria-label={`Ảnh ${i + 1}`}
                onClick={() => go(i)}
                className="relative h-1.5 w-10 overflow-hidden rounded-full bg-white/30 sm:w-14"
              >
                {i === index && (
                  <span
                    key={index}
                    className="progress absolute inset-y-0 left-0 rounded-full bg-[#f57c1f]"
                    style={{ animationDuration: `${MS}ms`, animationPlayState: paused ? "paused" : "running" }}
                  />
                )}
                {i < index && <span className="absolute inset-0 rounded-full bg-white/80" />}
              </button>
            ))}
          </div>
        </div>

        {/* 3 thẻ dịch vụ đè mép dưới */}
        <ul className="relative z-40 mx-auto -mt-20 grid max-w-[1400px] gap-4 px-4 sm:-mt-24 sm:px-8 md:grid-cols-3 md:gap-8">
          {CARDS.map((c, i) => (
            <li key={c.title} className="card-rise" style={{ animationDelay: `${700 + i * 90}ms` }}>
              <Link
                href={c.href}
                className={`svc-card group relative flex h-full items-center gap-5 overflow-hidden rounded-2xl p-6 shadow-[0_18px_40px_-18px_rgba(90,45,10,0.45)] ${
                  c.featured ? "bg-[#f57c1f] text-white" : "bg-white text-[#3b2a20]"
                }`}
              >
                {!c.featured && <span aria-hidden className="svc-fill absolute inset-0 bg-[#f57c1f]" />}
                <span
                  className={`svc-icon relative grid size-16 shrink-0 place-items-center rounded-full transition-colors ${
                    c.featured ? "bg-white/20 text-white" : "bg-[#fff1e3] text-[#f57c1f] group-hover:bg-white/20 group-hover:text-white"
                  }`}
                >
                  <c.icon className="size-8" strokeWidth={1.75} />
                </span>
                <span className="relative">
                  <span className={`block text-xl font-bold transition-colors ${c.featured ? "" : "group-hover:text-white"}`}>{c.title}</span>
                  <span className={`mt-1 block text-sm transition-colors ${c.featured ? "text-white/85" : "text-[#6b5a4e] group-hover:text-white/90"}`}>
                    {c.body}
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

/** Tách câu thành từng chữ, mỗi chữ trồi lên khỏi mặt nạ, trễ 50ms/chữ. */
function Words({ text, start, className }: { text: string; start: number; className?: string }) {
  return (
    <span className={className}>
      {text.split(" ").map((w, i) => (
        <span key={i} className="inline-block overflow-hidden pb-[0.08em] align-bottom">
          <span className="word-in inline-block" style={{ animationDelay: `${120 + (start + i) * 50}ms` }}>
            {w}&nbsp;
          </span>
        </span>
      ))}
    </span>
  );
}

/** Dấu chân viền trắng hiện lần lượt như đang bước chéo lên. */
function PawTrail() {
  const steps = [
    { x: "34%", y: "70%", r: -20 },
    { x: "38%", y: "58%", r: 10 },
    { x: "41%", y: "45%", r: -20 },
    { x: "45%", y: "33%", r: 10 },
    { x: "48%", y: "20%", r: -20 },
  ];
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 z-20 hidden md:block">
      {steps.map((p, i) => (
        <svg
          key={i}
          viewBox="0 0 40 40"
          className="paw-step absolute size-9 text-white"
          style={{ left: p.x, top: p.y, rotate: `${p.r}deg`, animationDelay: `${i * 0.45}s` }}
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <ellipse cx="20" cy="27" rx="8" ry="7" />
          <circle cx="9" cy="16" r="3.5" />
          <circle cx="16" cy="9" r="3.5" />
          <circle cx="25" cy="9" r="3.5" />
          <circle cx="31" cy="16" r="3.5" />
        </svg>
      ))}
    </div>
  );
}
