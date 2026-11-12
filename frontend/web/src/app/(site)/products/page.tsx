"use client";

/*
  Trang cửa hàng dựng theo ảnh mẫu (skill image-to-ui): banner cam nhạt có ô tìm kiếm, cột bộ
  lọc trái (danh mục, giá, thương hiệu) + thẻ ưu đãi, lưới 4 cột thẻ trắng, phân trang tròn,
  banner "Chăm sóc thú cưng" cuối trang. Đánh giá sao và % giảm giá trong ảnh mẫu KHÔNG có
  dữ liệu ở backend nên không hiển thị. "Thương hiệu" lọc bằng từ khoá tên (backend chưa có
  trường thương hiệu).
*/

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  ChevronDown,
  ChevronRight,
  Minus,
  PawPrint,
  Search,
  SlidersHorizontal,
} from "lucide-react";
import { categoryApi, productApi } from "@/lib/api";
import { formatPrice } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";
import { ShopCard } from "@/components/site/ShopCard";
import type { Product } from "@/types";
import { CareBanner } from "@/components/site/CareBanner";
import { Paw } from "@/components/site/Decor";

const PAGE_SIZE = 12;
const ORANGE = "#f57c1f";

const SORTS = [
  { value: "name,asc", label: "Tên A đến Z" },
  { value: "price,asc", label: "Giá thấp trước" },
  { value: "price,desc", label: "Giá cao trước" },
  { value: "createdAt,desc", label: "Mới nhập trước" },
] as const;

const PRICE_BANDS = [
  { label: "Dưới 100.000đ", min: undefined, max: 100_000 },
  { label: "100.000đ – 300.000đ", min: 100_000, max: 300_000 },
  { label: "300.000đ – 500.000đ", min: 300_000, max: 500_000 },
  { label: "Trên 500.000đ", min: 500_000, max: undefined },
] as const;

const BRANDS = ["Royal Canin", "Pedigree", "Whiskas", "Me-O", "Ganador"] as const;

export default function ProductsPage() {
  const [draft, setDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const [brand, setBrand] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [band, setBand] = useState(-1);
  const [sort, setSort] = useState<string>(SORTS[0].value);
  const [page, setPage] = useState(0);
  const [filtersOpen, setFiltersOpen] = useState(false);

  // Ô tìm kiếm ở header chuyển tới /products?q=…
  useEffect(() => {
    const q = new URLSearchParams(window.location.search).get("q");
    if (q) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- đọc query string một lần sau hydrate
      setDraft(q);
      setKeyword(q);
    }
  }, []);

  const categories = useQuery({ queryKey: ["categories"], queryFn: categoryApi.list });

  const price = band >= 0 ? PRICE_BANDS[band] : undefined;
  const filter = {
    keyword: [keyword, brand].filter(Boolean).join(" ") || undefined,
    categoryId: categoryId || undefined,
    minPrice: price?.min,
    maxPrice: price?.max,
    sort,
    page,
    size: PAGE_SIZE,
  };

  const products = useQuery({
    queryKey: ["products", filter],
    queryFn: () => productApi.search(filter),
    placeholderData: keepPreviousData,
  });

  const hasFilter = keyword !== "" || brand !== "" || categoryId !== "" || band >= 0;
  function reset() {
    setDraft("");
    setKeyword("");
    setBrand("");
    setCategoryId("");
    setBand(-1);
    setPage(0);
  }

  const data = products.data;
  const from = data ? data.page * data.size + 1 : 0;
  const to = data ? data.page * data.size + data.content.length : 0;

  return (
    <div className="bg-white">
      {/* ---------- Banner ---------- */}
      <div className="mx-auto max-w-[1440px] px-4 pt-4 sm:px-6">
        <section className="relative overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_0%_0%,#fffaf4_0%,#ffeedd_45%,#ffd9b3_100%)] md:min-h-[460px]">
          {/* Ảnh chó mèo nằm nửa phải, hiển thị gần đúng cỡ gốc (1511×528) để không vỡ nét */}
          <div className="absolute inset-y-0 right-0 hidden w-[68%] md:block">
            <Image
              src="/img/banner-shop.png"
              alt="Chú chó corgi và mèo con"
              fill
              priority
              unoptimized
              className="object-cover object-right [mask-image:linear-gradient(to_right,transparent,black_18%)]"
            />
          </div>
          <Paw className="left-[34%] top-10 size-10 rotate-[-18deg] text-[#fbb983]/70" />
          <Paw className="bottom-8 left-[6%] size-7 rotate-12 text-[#fbb983]/60" />
          <div className="relative grid min-h-[inherit] items-center">
            <div className="relative z-10 px-6 py-10 sm:px-12 md:max-w-[46%] md:py-14">
              <span className="inline-block rounded-full bg-[#ffe1c7] px-4 py-1.5 text-sm font-medium text-[#3b2a20]">
                Cửa hàng thú cưng
              </span>
              <h1 className="mt-5 text-[34px] font-extrabold leading-[1.15] text-[#2b1d14] sm:text-[42px]">
                Đồ dùng cho bé, chọn tại <span style={{ color: ORANGE }}>phòng khám</span>
              </h1>
              <p className="mt-4 max-w-sm text-[15px] text-[#4a3a30]">
                Sản phẩm chất lượng, an toàn cho thú cưng với giá tốt nhất từ phòng khám!
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
                  placeholder="Tìm kiếm sản phẩm, ví dụ: thức ăn, cát vệ sinh..."
                  aria-label="Tìm sản phẩm"
                  className="min-w-0 flex-1 bg-transparent text-sm text-[#2b1d14] outline-none placeholder:text-[#9a8b80]"
                />
                <button
                  type="submit"
                  aria-label="Tìm"
                  className="grid size-10 shrink-0 place-items-center rounded-full text-white"
                  style={{ background: ORANGE }}
                >
                  <Search className="size-5" />
                </button>
              </form>
            </div>
          </div>
        </section>
      </div>

      {/* ---------- Nội dung ---------- */}
      <div className="mx-auto grid max-w-[1440px] gap-6 px-4 py-8 sm:px-6 lg:grid-cols-[240px_1fr]">
        <div className="lg:hidden">
          <button
            onClick={() => setFiltersOpen((v) => !v)}
            aria-expanded={filtersOpen}
            className="inline-flex h-10 items-center gap-2 rounded-full border border-[#eee] px-4 text-sm"
          >
            <SlidersHorizontal className="size-4" /> Bộ lọc
          </button>
        </div>

        <aside className={cn("space-y-5 lg:block", filtersOpen ? "block" : "hidden")}>
          <div className="rounded-3xl bg-white p-5 shadow-[0_4px_24px_-8px_rgba(0,0,0,0.12)]">
            <h2 className="flex items-center gap-2 text-lg font-bold text-[#2b1d14]">
              <SlidersHorizontal className="size-5" /> Bộ lọc
            </h2>

            <FilterSection title="Danh mục">
              <div className="max-h-72 space-y-1 overflow-y-auto pr-1">
                <CatButton active={categoryId === ""} onClick={() => { setCategoryId(""); setPage(0); }}>
                  Tất cả sản phẩm
                </CatButton>
                {(categories.data ?? []).map((c) => (
                  <CatButton key={c.id} active={categoryId === c.id} onClick={() => { setCategoryId(c.id); setPage(0); }}>
                    {c.name}
                  </CatButton>
                ))}
              </div>
            </FilterSection>

            <FilterSection title="Giá tiền">
              <div className="space-y-3">
                {PRICE_BANDS.map((b, i) => (
                  <label key={b.label} className="flex cursor-pointer items-center gap-3 text-sm text-[#3b2a20]">
                    <input
                      type="radio"
                      name="price"
                      checked={band === i}
                      onClick={() => { setBand(band === i ? -1 : i); setPage(0); }}
                      onChange={() => {}}
                      className="size-4 accent-[#f57c1f]"
                    />
                    {b.label}
                  </label>
                ))}
              </div>
            </FilterSection>

            <FilterSection title="Thương hiệu">
              <div className="space-y-3">
                {BRANDS.map((b) => (
                  <label key={b} className="flex cursor-pointer items-center gap-3 text-sm text-[#3b2a20]">
                    <input
                      type="checkbox"
                      checked={brand === b}
                      onChange={() => { setBrand(brand === b ? "" : b); setPage(0); }}
                      className="size-4 rounded accent-[#f57c1f]"
                    />
                    {b}
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

          <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#fff0e2] to-[#ffdcbc] p-5">
            <PawPrint aria-hidden className="absolute right-4 top-4 size-6 text-[#f9b27a]" />
            <p className="max-w-[9rem] text-[17px] font-bold leading-snug text-[#2b1d14]">Ưu đãi hấp dẫn cho thú cưng</p>
            <Link
              href="/products"
              onClick={reset}
              className="mt-4 inline-flex h-8 items-center gap-1 rounded-full px-4 text-xs font-semibold text-white"
              style={{ background: ORANGE }}
            >
              Xem ngay <ArrowRight className="size-3.5" />
            </Link>
          </div>
        </aside>

        <div className="min-w-0">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-[#3b2a20]" aria-live="polite">
              {products.isLoading
                ? "Đang tải sản phẩm"
                : data && data.totalElements > 0
                  ? `Hiển thị ${from}–${to} / ${data.totalElements} sản phẩm`
                  : ""}
            </p>
            <label className="flex items-center gap-3 text-sm text-[#3b2a20]">
              Sắp xếp theo:
              <span className="relative">
                <select
                  value={sort}
                  onChange={(e) => { setSort(e.target.value); setPage(0); }}
                  className="h-10 appearance-none rounded-full border border-[#eee] bg-white pl-4 pr-10 font-medium shadow-sm outline-none"
                >
                  {SORTS.map((s) => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                  ))}
                </select>
                <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2" />
              </span>
            </label>
          </div>

          {products.isLoading ? (
            <ul className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4" aria-hidden>
              {Array.from({ length: 8 }).map((_, i) => (
                <li key={i} className="h-[320px] animate-pulse rounded-3xl bg-[#fff3e8]" />
              ))}
            </ul>
          ) : products.isError ? (
            <EmptyBox title="Chưa lấy được danh sách sản phẩm" body="Kết nối tới phòng khám đang trục trặc. Thử lại sau vài giây." action={() => products.refetch()} actionLabel="Thử lại" />
          ) : (data?.content ?? []).length === 0 ? (
            <EmptyBox
              title="Không có món nào khớp"
              body={hasFilter ? "Thử bỏ bớt bộ lọc, hoặc tìm bằng một từ ngắn hơn." : "Phòng khám chưa đưa sản phẩm nào lên."}
              action={hasFilter ? reset : undefined}
              actionLabel="Bỏ hết bộ lọc"
            />
          ) : (
            <>
              <ul className={cn("grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4", products.isFetching && "opacity-60")}>
                {data!.content.map((p) => (
                  <li key={p.id}>
                    <ShopCard product={p} />
                  </li>
                ))}
              </ul>
              {data!.totalPages > 1 && (
                <Pager
                  page={data!.page}
                  totalPages={data!.totalPages}
                  onChange={(p) => { setPage(p); window.scrollTo({ top: 0, behavior: "smooth" }); }}
                />
              )}
            </>
          )}
        </div>
      </div>

      <CareBanner
        title={<>Chăm sóc thú cưng<br />là yêu thương</>}
        subtitle="Đồng hành cùng bạn và thú cưng mỗi ngày"
        cta={{ href: "/appointments/create", label: "Khám phá ngay" }}
        image={{ src: "/img/cho-meo-ngu.png", alt: "Chú chó con và mèo con ngủ cạnh nhau", width: 672, height: 230 }}
      />
    </div>
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

function CatButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-xl px-2 py-1.5 text-left text-sm transition",
        active ? "font-semibold" : "text-[#3b2a20] hover:bg-[#fff3e8]",
      )}
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
  const start = Math.max(0, Math.min(page - 1, totalPages - 4));
  const pages = Array.from({ length: Math.min(4, totalPages) }, (_, i) => start + i);
  const btn = "grid size-9 place-items-center rounded-full border text-sm transition";
  return (
    <nav aria-label="Phân trang" className="mt-10 flex items-center justify-center gap-3">
      {page > 0 && (
        <button className={cn(btn, "border-[#eee] bg-white")} onClick={() => onChange(page - 1)} aria-label="Trang trước">
          <ChevronRight className="size-4 rotate-180" />
        </button>
      )}
      {pages.map((p) => (
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

