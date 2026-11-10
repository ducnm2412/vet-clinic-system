"use client";

/*
  Trang dịch vụ — cùng bố cục với trang sản phẩm: banner cam có ô tìm kiếm, cột bộ lọc trái,
  lưới thẻ, phân trang, banner cuối sát footer. Chỉ hiển thị dịch vụ khám (booking-service).
  Backend trả cả danh sách một lần nên tìm kiếm / lọc / sắp xếp / phân trang làm ở phía trình duyệt.
  "Nhóm dịch vụ" là cách nhóm của giao diện theo slug, backend chưa có trường nhóm.
*/

import { useMemo, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, CalendarDays, ChevronDown, ChevronRight, Clock, Minus, PawPrint, Search, SlidersHorizontal } from "lucide-react";
import { clinicServiceApi } from "@/lib/api";
import { mockServiceDetails } from "@/lib/api/mock-data"; // MOCK-TẠM: ảnh đầu bài làm ảnh thẻ
import { extraServiceArticles } from "@/lib/content/service-content"; // MOCK-TẠM
import { formatPrice } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { CareBanner } from "@/components/site/CareBanner";
import { Paw } from "@/components/site/Decor";
import type { ClinicService } from "@/types";

const ORANGE = "#f57c1f";
const GREEN = "#1f4a3c";
const PAGE_SIZE = 8;
const FALLBACK_IMAGES = ["/img/meo.jpg", "/img/cho-con.jpg", "/img/hero.jpg", "/img/cham-soc.jpg"];

const GROUPS: Array<{ key: string; label: string; slugs: string[] }> = [
  { key: "kham", label: "Khám & chẩn đoán", slugs: ["cap-cuu-24-7", "kham-tong-quat", "kham-dieu-tri", "xet-nghiem", "sieu-am"] },
  { key: "dieu-tri", label: "Phòng bệnh & điều trị", slugs: ["tiem-phong", "phau-thuat", "rang-mieng"] },
  { key: "cham-soc", label: "Làm đẹp", slugs: ["spa-grooming"] },
  { key: "khac", label: "Dịch vụ khác", slugs: [] },
];
function groupOf(slug: string) {
  return GROUPS.find((g) => g.slugs.includes(slug))?.key ?? "khac";
}

const PRICE_BANDS = [
  { label: "Dưới 200.000đ", min: 0, max: 200_000 },
  { label: "200.000đ – 500.000đ", min: 200_000, max: 500_000 },
  { label: "Trên 500.000đ", min: 500_000, max: Infinity },
] as const;

const SORTS = [
  { value: "order", label: "Phổ biến nhất" },
  { value: "price-asc", label: "Giá thấp trước" },
  { value: "price-desc", label: "Giá cao trước" },
  { value: "name", label: "Tên A đến Z" },
] as const;

function imageOf(s: ClinicService, i: number) {
  return (mockServiceDetails[s.slug] ?? extraServiceArticles[s.slug])?.blocks.find((b) => b[0] === "img")?.[1] ?? FALLBACK_IMAGES[i % FALLBACK_IMAGES.length];
}

export default function ServicesPage() {
  const [draft, setDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const [group, setGroup] = useState("");
  const [band, setBand] = useState(-1);
  const [sort, setSort] = useState<string>("order");
  const [page, setPage] = useState(0);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });
  const active = useMemo(() => (services.data ?? []).filter((s) => s.active), [services.data]);

  const filtered = useMemo(() => {
    const k = keyword.toLowerCase();
    const b = band >= 0 ? PRICE_BANDS[band] : null;
    const list = active.filter(
      (s) =>
        (!k || s.name.toLowerCase().includes(k) || (s.description ?? "").toLowerCase().includes(k)) &&
        (!group || groupOf(s.slug) === group) &&
        (!b || (s.referencePrice != null && s.referencePrice >= b.min && s.referencePrice < b.max)),
    );
    const price = (s: ClinicService) => s.referencePrice ?? 0;
    return [...list].sort((a, c) =>
      sort === "price-asc" ? price(a) - price(c) : sort === "price-desc" ? price(c) - price(a) : sort === "name" ? a.name.localeCompare(c.name, "vi") : a.displayOrder - c.displayOrder,
    );
  }, [active, keyword, group, band, sort]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const current = Math.min(page, totalPages - 1);
  const pageItems = filtered.slice(current * PAGE_SIZE, current * PAGE_SIZE + PAGE_SIZE);
  const hasFilter = keyword !== "" || group !== "" || band >= 0;

  function reset() {
    setDraft("");
    setKeyword("");
    setGroup("");
    setBand(-1);
    setPage(0);
  }

  return (
    <div className="bg-white">
      {/* ---------- Banner ---------- */}
      <div className="mx-auto max-w-[1440px] px-4 pt-4 sm:px-6">
        <section className="relative overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_0%_0%,#fffaf4_0%,#ffeedd_45%,#ffd9b3_100%)] md:min-h-[420px]">
          <div aria-hidden className="absolute -bottom-24 right-[4%] hidden aspect-square h-[115%] rounded-full bg-[radial-gradient(circle,#ffc48f_0%,#f7ad62_55%,transparent_72%)] md:block" />
          <div
            className="absolute bottom-0 right-[6%] hidden aspect-[460/338] h-[92%] md:block"
            style={{ maskImage: "radial-gradient(ellipse 62% 70% at 50% 58%, #000 60%, transparent 100%)", WebkitMaskImage: "radial-gradient(ellipse 62% 70% at 50% 58%, #000 60%, transparent 100%)" }}
          >
            <Image src="/img/dv-hero.png" alt="Chú chó golden và mèo con" fill priority unoptimized className="object-cover" />
          </div>
          <Paw className="left-[44%] top-10 size-9 -rotate-12 text-[#f9a55c]" />
          <Paw className="right-6 top-[38%] hidden size-10 rotate-12 text-white md:block" />

          <div className="relative z-10 px-6 py-10 sm:px-12 md:max-w-[50%] md:py-14">
            <span className="inline-block rounded-full bg-[#ffe1c7] px-4 py-1.5 text-sm font-medium text-[#3b2a20]">Dịch vụ thú y</span>
            <h1 className="mt-5 text-[34px] font-extrabold leading-[1.15] text-[#2b1d14] sm:text-[42px]">
              Dịch vụ khám chữa bệnh <span style={{ color: ORANGE }}>cho thú cưng</span>
            </h1>
            <p className="mt-4 max-w-sm text-[15px] text-[#4a3a30]">
              Bác sĩ có chuyên môn, trang thiết bị hiện đại, đặt lịch online chỉ trong một phút.
            </p>
            <form
              role="search"
              onSubmit={(e) => {
                e.preventDefault();
                setKeyword(draft.trim());
                setPage(0);
              }}
              className="mt-7 flex max-w-md items-center rounded-full bg-white p-1.5 pl-5 shadow-[0_8px_24px_-12px_rgba(245,124,31,0.45)]"
            >
              <input
                type="search"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="Tìm dịch vụ, ví dụ: tiêm phòng, spa..."
                aria-label="Tìm dịch vụ"
                className="min-w-0 flex-1 bg-transparent text-sm text-[#2b1d14] outline-none placeholder:text-[#9a8b80]"
              />
              <button type="submit" aria-label="Tìm" className="grid size-10 shrink-0 place-items-center rounded-full text-white" style={{ background: ORANGE }}>
                <Search className="size-5" />
              </button>
            </form>
          </div>
        </section>
      </div>

      {/* ---------- Nội dung ---------- */}
      <div className="mx-auto grid max-w-[1440px] gap-6 px-4 py-8 sm:px-6 lg:grid-cols-[240px_1fr]">
        <div className="lg:hidden">
          <button onClick={() => setFiltersOpen((v) => !v)} aria-expanded={filtersOpen} className="inline-flex h-10 items-center gap-2 rounded-full border border-[#eee] px-4 text-sm">
            <SlidersHorizontal className="size-4" /> Bộ lọc
          </button>
        </div>

        <aside className={cn("space-y-5 lg:block", filtersOpen ? "block" : "hidden")}>
          <div className="rounded-3xl bg-white p-5 shadow-[0_4px_24px_-8px_rgba(0,0,0,0.12)]">
            <h2 className="flex items-center gap-2 text-lg font-bold text-[#2b1d14]">
              <SlidersHorizontal className="size-5" /> Bộ lọc
            </h2>

            <FilterSection title="Nhóm dịch vụ">
              <div className="space-y-1">
                <GroupButton active={group === ""} onClick={() => { setGroup(""); setPage(0); }}>Tất cả dịch vụ</GroupButton>
                {GROUPS.filter((g) => active.some((s) => groupOf(s.slug) === g.key)).map((g) => (
                  <GroupButton key={g.key} active={group === g.key} onClick={() => { setGroup(g.key); setPage(0); }}>
                    {g.label}
                  </GroupButton>
                ))}
              </div>
            </FilterSection>

            <FilterSection title="Giá tham khảo">
              <div className="space-y-3">
                {PRICE_BANDS.map((b, i) => (
                  <label key={b.label} className="flex cursor-pointer items-center gap-3 text-sm text-[#3b2a20]">
                    <input type="radio" name="price" checked={band === i} onClick={() => { setBand(band === i ? -1 : i); setPage(0); }} onChange={() => {}} className="size-4 accent-[#f57c1f]" />
                    {b.label}
                  </label>
                ))}
              </div>
            </FilterSection>

            {hasFilter && (
              <button onClick={reset} className="mt-5 text-sm font-medium underline underline-offset-4" style={{ color: ORANGE }}>
                Bỏ hết bộ lọc
              </button>
            )}
          </div>

          <div className="relative overflow-hidden rounded-3xl p-5 text-white" style={{ background: GREEN }}>
            <PawPrint aria-hidden className="absolute right-4 top-4 size-6 text-[#f9a55c]" />
            <p className="max-w-[10rem] text-[17px] font-bold leading-snug">Cần tư vấn trước khi đặt lịch?</p>
            <Link href="/appointments/create" className="mt-4 inline-flex h-8 items-center gap-1 rounded-full px-4 text-xs font-semibold text-white" style={{ background: ORANGE }}>
              Đặt lịch ngay <ArrowRight className="size-3.5" />
            </Link>
          </div>
        </aside>

        <div className="min-w-0">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-[#3b2a20]" aria-live="polite">
              {services.isLoading
                ? "Đang tải dịch vụ"
                : filtered.length > 0
                  ? `Hiển thị ${current * PAGE_SIZE + 1}–${current * PAGE_SIZE + pageItems.length} / ${filtered.length} dịch vụ`
                  : ""}
            </p>
            <label className="flex items-center gap-3 text-sm text-[#3b2a20]">
              Sắp xếp theo:
              <span className="relative">
                <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(0); }} className="h-10 appearance-none rounded-full border border-[#eee] bg-white pl-4 pr-10 font-medium shadow-sm outline-none">
                  {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
                <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2" />
              </span>
            </label>
          </div>

          {services.isLoading ? (
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-hidden>
              {Array.from({ length: 8 }).map((_, i) => <li key={i} className="h-[340px] animate-pulse rounded-3xl bg-[#fff3e8]" />)}
            </ul>
          ) : services.isError ? (
            <EmptyBox title="Chưa lấy được danh sách dịch vụ" body="Kết nối tới phòng khám đang trục trặc. Thử lại sau vài giây." action={() => services.refetch()} actionLabel="Thử lại" />
          ) : pageItems.length === 0 ? (
            <EmptyBox title="Không có dịch vụ nào khớp" body="Thử bỏ bớt bộ lọc, hoặc tìm bằng một từ ngắn hơn." action={hasFilter ? reset : undefined} actionLabel="Bỏ hết bộ lọc" />
          ) : (
            <>
              <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
                {pageItems.map((s) => (
                  <li key={s.id}>
                    <ServiceCard service={s} image={imageOf(s, active.indexOf(s))} />
                  </li>
                ))}
              </ul>
              {totalPages > 1 && (
                <Pager page={current} totalPages={totalPages} onChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: "smooth" }); }} />
              )}
            </>
          )}
        </div>
      </div>

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

function ServiceCard({ service, image }: { service: ClinicService; image: string }) {
  return (
    <article className="group flex h-full flex-col rounded-3xl bg-white p-3 shadow-[0_4px_24px_-10px_rgba(0,0,0,0.15)] transition hover:-translate-y-0.5 hover:shadow-[0_10px_30px_-10px_rgba(245,124,31,0.35)]">
      <Link href={`/services/${service.slug}`} className="relative block aspect-[4/3] overflow-hidden rounded-2xl">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={image} alt={service.name} loading="lazy" className="size-full object-cover transition duration-500 group-hover:scale-105" />
        <span className="absolute left-3 top-3 inline-flex items-center gap-1 rounded-full bg-white/90 px-2.5 py-1 text-[11px] font-semibold text-[#2b1d14]">
          <Clock className="size-3" style={{ color: ORANGE }} /> {service.durationMinutes} phút
        </span>
      </Link>
      <div className="flex flex-1 flex-col px-2 pb-1 pt-3">
        <h3 className="text-[15px] font-bold leading-snug text-[#2b1d14]">
          <Link href={`/services/${service.slug}`} className="hover:text-[#f57c1f]">{service.name}</Link>
        </h3>
        {service.description && <p className="mt-1.5 line-clamp-2 text-[13px] leading-relaxed text-[#6b5a4e]">{service.description}</p>}
        {service.referencePrice != null && (
          <p className="mt-2 text-[17px] font-bold" style={{ color: ORANGE }}>Từ {formatPrice(service.referencePrice)}</p>
        )}
        <div className="mt-auto flex gap-2 pt-3">
          <Link
            href={`/appointments/create?service=${service.slug}`}
            className="inline-flex h-10 flex-1 items-center justify-center gap-1.5 rounded-full bg-gradient-to-r from-[#ff8a2a] to-[#f2661b] text-sm font-semibold text-white shadow-[0_6px_14px_-6px_rgba(245,124,31,0.8)]"
          >
            <CalendarDays className="size-4" /> Đặt lịch
          </Link>
          <Link href={`/services/${service.slug}`} aria-label={`Chi tiết ${service.name}`} className="grid size-10 place-items-center rounded-full border border-[#eee] text-[#2b1d14] transition hover:border-[#f57c1f] hover:text-[#f57c1f]">
            <ArrowRight className="size-4" />
          </Link>
        </div>
      </div>
    </article>
  );
}

function FilterSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mt-5 border-t border-[#f1ece8] pt-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="font-bold text-[#2b1d14]">{title}</h3>
        <span className="grid size-5 place-items-center rounded-full bg-[#ffd9bd]">
          <Minus className="size-3" style={{ color: ORANGE }} />
        </span>
      </div>
      {children}
    </div>
  );
}

function GroupButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={cn("flex w-full items-center gap-2.5 rounded-xl px-2 py-1.5 text-left text-sm transition", active ? "font-semibold" : "text-[#3b2a20] hover:bg-[#fff3e8]")}
      style={active ? { color: ORANGE } : undefined}
    >
      <span className={cn("grid size-6 shrink-0 place-items-center rounded-full", active ? "bg-[#ffe1c7]" : "")}>
        <PawPrint className="size-3.5" />
      </span>
      {children}
    </button>
  );
}

function EmptyBox({ title, body, action, actionLabel }: { title: string; body: string; action?: () => void; actionLabel: string }) {
  return (
    <div className="rounded-3xl bg-[#fff6ee] px-6 py-14 text-center">
      <p className="text-lg font-bold text-[#2b1d14]">{title}</p>
      <p className="mt-2 text-sm text-[#6b5a4e]">{body}</p>
      {action && (
        <button onClick={action} className="mt-5 h-10 rounded-full px-5 text-sm font-semibold text-white" style={{ background: ORANGE }}>
          {actionLabel}
        </button>
      )}
    </div>
  );
}

function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  const btn = "grid size-9 place-items-center rounded-full border text-sm transition";
  return (
    <nav aria-label="Phân trang" className="mt-10 flex items-center justify-center gap-3">
      {page > 0 && (
        <button className={cn(btn, "border-[#eee] bg-white")} onClick={() => onChange(page - 1)} aria-label="Trang trước">
          <ChevronRight className="size-4 rotate-180" />
        </button>
      )}
      {Array.from({ length: totalPages }, (_, p) => (
        <button
          key={p}
          onClick={() => onChange(p)}
          aria-current={p === page ? "page" : undefined}
          className={cn(btn, p === page ? "border-transparent font-semibold text-white" : "border-[#eee] bg-white text-[#2b1d14]")}
          style={p === page ? { background: ORANGE } : undefined}
        >
          {p + 1}
        </button>
      ))}
      {page < totalPages - 1 && (
        <button className={cn(btn, "border-[#eee] bg-white")} onClick={() => onChange(page + 1)} aria-label="Trang sau">
          <ChevronRight className="size-4" />
        </button>
      )}
    </nav>
  );
}
