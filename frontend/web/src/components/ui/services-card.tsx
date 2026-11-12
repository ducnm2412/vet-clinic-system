"use client";

/*
  Carousel thẻ dịch vụ (chuyển thể từ component "animated service card" theo style shadcn).
  - Không dùng shadcn Button: dự án đã có components/ui/Button.tsx với API riêng, và trên Windows
    file button.tsx sẽ trùng tên (không phân biệt hoa thường) → dùng <button> thường.
  - Thẻ trồi lên lần lượt khi cuộn tới (framer-motion), kéo/vuốt bằng embla, phím ← → để chuyển.
*/

import * as React from "react";
import Link from "next/link";
import { motion, useInView } from "framer-motion";
import useEmblaCarousel from "embla-carousel-react";
import Image from "next/image";
import { ArrowLeft, ArrowRight, CalendarDays } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface Service {
  id: string;
  title: string;
  description: string;
  image: string;
  /** Lớp Tailwind gradient, vd "from-orange-100 to-orange-200" */
  gradient: string;
  href?: string;
  price?: string;
  bookHref?: string;
}

function ServiceCard({ service, index }: { service: Service; index: number }) {
  const Title = service.href ? Link : "span";
  return (
    <motion.div
      variants={{
        hidden: { opacity: 0, y: 50 },
        visible: { opacity: 1, y: 0, transition: { duration: 0.5, delay: index * 0.1 } },
      }}
      className={cn(
        "group relative flex h-full flex-col overflow-hidden rounded-3xl bg-gradient-to-br p-3 transition-shadow duration-300 hover:shadow-[0_18px_40px_-18px_rgba(90,45,10,0.45)]",
        service.gradient,
      )}
    >
      <div className="relative aspect-[16/10] overflow-hidden rounded-2xl">
        <Image src={service.image} alt="" fill sizes="(max-width:640px) 100vw, (max-width:1280px) 33vw, 25vw" className="object-cover transition duration-500 group-hover:scale-105" />
      </div>
      <div className="flex flex-1 flex-col px-3 pb-2 pt-4">
        <h3 className="mb-2 line-clamp-2 min-h-[3.5rem] text-lg font-bold uppercase leading-7 tracking-wide text-[#2b1d14]">
          {/* after: phủ cả thẻ để bấm vào đâu cũng sang trang chi tiết */}
          <Title href={service.href ?? ""} className="after:absolute after:inset-0">{service.title}</Title>
        </h3>
        <p className="line-clamp-2 min-h-10 text-sm text-[#3b2a20]/75">{service.description}</p>
        <div className="mt-auto flex items-center justify-between gap-3 pt-4">
          <span className="text-sm font-semibold text-[#f57c1f]">{service.price}</span>
          {service.bookHref && (
            <Link
              href={service.bookHref}
              className="press relative z-10 inline-flex h-9 shrink-0 items-center gap-1.5 rounded-full bg-[#f57c1f] px-4 text-xs font-bold text-white shadow-[0_8px_18px_-8px_rgba(245,124,31,0.9)] transition hover:bg-[#e06a10]"
            >
              <CalendarDays className="size-4" /> Đặt lịch khám
            </Link>
          )}
        </div>
      </div>
    </motion.div>
  );
}

export function ServiceCarousel({ services, className }: { services: Service[]; className?: string }) {
  const ref = React.useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, amount: 0.2 });
  const [emblaRef, api] = useEmblaCarousel({ align: "start", loop: true });

  const prev = React.useCallback(() => api?.scrollPrev(), [api]);
  const next = React.useCallback(() => api?.scrollNext(), [api]);

  const navBtn =
    "press absolute top-1/2 z-20 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-white text-[#2b1d14] shadow-md transition-colors hover:bg-[#f57c1f] hover:text-white";

  return (
    <div
      ref={ref}
      role="region"
      aria-roledescription="carousel"
      aria-label="Dịch vụ"
      className={cn("relative", className)}
      onKeyDownCapture={(e) => {
        if (e.key === "ArrowLeft") {
          e.preventDefault();
          prev();
        } else if (e.key === "ArrowRight") {
          e.preventDefault();
          next();
        }
      }}
    >
      <motion.div initial="hidden" animate={isInView ? "visible" : "hidden"}>
        <div ref={emblaRef} className="overflow-hidden">
          <div className="-ml-5 flex">
            {services.map((s, i) => (
              <div key={s.id} role="group" aria-roledescription="slide" className="min-w-0 shrink-0 grow-0 basis-full pl-5 sm:basis-1/2 lg:basis-1/3 xl:basis-1/4">
                <ServiceCard service={s} index={i} />
              </div>
            ))}
          </div>
        </div>
      </motion.div>
      <button type="button" onClick={prev} aria-label="Dịch vụ trước" className={cn(navBtn, "-left-3")}>
        <ArrowLeft className="size-5" />
      </button>
      <button type="button" onClick={next} aria-label="Dịch vụ sau" className={cn(navBtn, "-right-3")}>
        <ArrowRight className="size-5" />
      </button>
    </div>
  );
}
