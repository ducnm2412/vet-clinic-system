"use client";

import { useState } from "react";
import Image from "next/image";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Search, SlidersHorizontal, X } from "lucide-react";
import { categoryApi, productApi } from "@/lib/api";
import { cn } from "@/lib/utils/cn";
import { Container, SiteButton } from "@/components/site/primitives";
import { ProductCard } from "@/components/site/ProductCard";

const PAGE_SIZE = 12;

/** Chỉ ba cột này sắp xếp được — Spring Pageable đọc thẳng tên trường của entity. */
const SORTS = [
  { value: "name,asc", label: "Tên A đến Z" },
  { value: "price,asc", label: "Giá thấp trước" },
  { value: "price,desc", label: "Giá cao trước" },
  { value: "createdAt,desc", label: "Mới nhập trước" },
] as const;

const PRICE_BANDS = [
  { label: "Dưới 100k", min: undefined, max: 100_000 },
  { label: "100k – 300k", min: 100_000, max: 300_000 },
  { label: "300k – 600k", min: 300_000, max: 600_000 },
  { label: "Trên 600k", min: 600_000, max: undefined },
] as const;

export default function ProductsPage() {
  // Ô tìm kiếm gõ đến đâu nằm ở `draft`, chỉ khi bấm tìm mới đẩy sang `keyword` — tránh
  // gọi backend sau mỗi phím bấm, và cũng tránh lưới sản phẩm nhảy loạn khi đang gõ.
  const [draft, setDraft] = useState("");
  const [keyword, setKeyword] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [band, setBand] = useState(-1);
  const [sort, setSort] = useState<string>(SORTS[0].value);
  const [page, setPage] = useState(0);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const categories = useQuery({ queryKey: ["categories"], queryFn: categoryApi.list });

  const price = band >= 0 ? PRICE_BANDS[band] : undefined;
  const filter = {
    keyword: keyword || undefined,
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
    // Giữ kết quả cũ khi đổi trang để lưới không nháy trắng rồi hiện lại.
    placeholderData: keepPreviousData,
  });

  const hasFilter = keyword !== "" || categoryId !== "" || band >= 0;

  function reset() {
    setDraft("");
    setKeyword("");
    setCategoryId("");
    setBand(-1);
    setPage(0);
  }

  const data = products.data;

  return (
    <>
      <div className="relative overflow-hidden bg-peach py-14 md:py-20">
        {/*
          Mái vòm ảnh đứng trên đúng ranh giới dưới của dải — không có nó thì nửa phải
          trống trơn trên màn hình rộng. Chỉ là trang trí nên ẩn hẳn ở khổ hẹp.
        */}
        <div
          aria-hidden
          className="arch absolute bottom-0 right-[6vw] hidden aspect-[3/4] w-64 lg:block"
        >
          <Image src="/img/meo.jpg" alt="" fill sizes="16rem" className="object-cover" />
        </div>

        <Container className="relative">
          <h1 className="t-h2 max-w-[18ch]">Đồ dùng cho bé, chọn tại phòng khám</h1>
          <p className="measure t-lede mt-4 text-stone">
            Thức ăn, phụ kiện và đồ chăm sóc. Số lượng hiển thị là tồn kho thật tại phòng khám,
            cập nhật theo từng đơn.
          </p>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              setKeyword(draft.trim());
              setPage(0);
            }}
            className="mt-8 flex max-w-xl gap-2"
            role="search"
          >
            <div className="relative flex-1">
              <Search
                aria-hidden
                className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-stone"
              />
              <input
                type="search"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="Tìm hạt, sữa tắm, vòng cổ…"
                aria-label="Tìm sản phẩm"
                className="h-12 w-full rounded-full border border-mist bg-white pl-11 pr-4 text-[16px] text-pine outline-none transition-colors placeholder:text-stone/70 focus:border-teal"
              />
            </div>
            <SiteButton type="submit" variant="teal">
              Tìm
            </SiteButton>
          </form>
        </Container>
      </div>

      <Container className="py-10 md:py-14">
        <div className="grid gap-10 lg:grid-cols-[15rem_1fr] lg:gap-14">
          {/*
            Trên điện thoại bộ lọc gập lại sau một nút, không chiếm hết màn hình đầu tiên
            trước khi người ta kịp nhìn thấy sản phẩm nào.
          */}
          <div className="lg:hidden">
            <button
              onClick={() => setFiltersOpen((v) => !v)}
              aria-expanded={filtersOpen}
              className="inline-flex h-11 items-center gap-2 rounded-full border border-mist px-4 text-[15px] text-pine"
            >
              <SlidersHorizontal aria-hidden className="size-4" />
              Bộ lọc
              {hasFilter && <span className="size-2 rounded-full bg-coral-deep" />}
            </button>
          </div>

          <aside className={cn("lg:block", filtersOpen ? "block" : "hidden")}>
            <FilterGroup title="Danh mục">
              <FilterPill active={categoryId === ""} onClick={() => { setCategoryId(""); setPage(0); }}>
                Tất cả
              </FilterPill>
              {(categories.data ?? []).map((c) => (
                <FilterPill
                  key={c.id}
                  active={categoryId === c.id}
                  onClick={() => {
                    setCategoryId(c.id);
                    setPage(0);
                  }}
                >
                  {c.name}
                </FilterPill>
              ))}
            </FilterGroup>

            <FilterGroup title="Khoảng giá">
              <FilterPill active={band < 0} onClick={() => { setBand(-1); setPage(0); }}>
                Không giới hạn
              </FilterPill>
              {PRICE_BANDS.map((b, i) => (
                <FilterPill
                  key={b.label}
                  active={band === i}
                  onClick={() => {
                    setBand(i);
                    setPage(0);
                  }}
                >
                  {b.label}
                </FilterPill>
              ))}
            </FilterGroup>

            {hasFilter && (
              <button
                onClick={reset}
                className="mt-6 inline-flex items-center gap-1.5 text-[15px] text-teal-deep underline underline-offset-4"
              >
                <X aria-hidden className="size-4" />
                Bỏ hết bộ lọc
              </button>
            )}
          </aside>

          <div className="min-w-0">
            <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
              <p className="text-stone" aria-live="polite">
                {products.isLoading
                  ? "Đang tải sản phẩm"
                  : data
                    ? `${data.totalElements} sản phẩm`
                    : ""}
              </p>

              <label className="flex items-center gap-2 text-[15px] text-stone">
                Sắp xếp
                <select
                  value={sort}
                  onChange={(e) => {
                    setSort(e.target.value);
                    setPage(0);
                  }}
                  className="h-11 rounded-full border border-mist bg-white px-4 text-pine outline-none focus:border-teal"
                >
                  {SORTS.map((s) => (
                    <option key={s.value} value={s.value}>
                      {s.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {products.isLoading ? (
              <ProductGridSkeleton />
            ) : products.isError ? (
              <div className="rounded-[var(--radius-card)] border border-mist bg-mint px-6 py-12 text-center">
                <p className="t-h3">Chưa lấy được danh sách sản phẩm</p>
                <p className="mt-2 text-stone">
                  Kết nối tới phòng khám đang trục trặc. Thử lại sau vài giây.
                </p>
                <SiteButton variant="outline" className="mt-6" onClick={() => products.refetch()}>
                  Thử lại
                </SiteButton>
              </div>
            ) : (data?.content ?? []).length === 0 ? (
              <div className="rounded-[var(--radius-card)] border border-mist bg-mint px-6 py-16 text-center">
                <p className="t-h3">Không có món nào khớp</p>
                <p className="measure mx-auto mt-2 text-stone">
                  {hasFilter
                    ? "Thử bỏ bớt bộ lọc, hoặc tìm bằng một từ ngắn hơn."
                    : "Phòng khám chưa đưa sản phẩm nào lên. Bạn quay lại sau nhé."}
                </p>
                {hasFilter && (
                  <SiteButton variant="outline" className="mt-6" onClick={reset}>
                    Bỏ hết bộ lọc
                  </SiteButton>
                )}
              </div>
            ) : (
              <>
                <ul
                  className={cn(
                    "grid grid-cols-2 gap-x-6 gap-y-10 xl:grid-cols-3",
                    products.isFetching && "opacity-60 transition-opacity",
                  )}
                >
                  {(data?.content ?? []).map((p) => (
                    <li key={p.id}>
                      <ProductCard product={p} />
                    </li>
                  ))}
                </ul>

                {data && data.totalPages > 1 && (
                  <Pager
                    page={data.page}
                    totalPages={data.totalPages}
                    onChange={(p) => {
                      setPage(p);
                      window.scrollTo({ top: 0, behavior: "smooth" });
                    }}
                  />
                )}
              </>
            )}
          </div>
        </div>
      </Container>
    </>
  );
}

function FilterGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mt-8 first:mt-0">
      <h2 className="font-[family-name:var(--font-brand)] text-[17px] font-semibold">{title}</h2>
      <div className="mt-3 flex flex-wrap gap-2">{children}</div>
    </div>
  );
}

function FilterPill({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "h-10 rounded-full px-4 text-[15px] transition-colors",
        active
          ? "bg-pine text-white"
          : "border border-mist bg-white text-stone hover:border-teal hover:text-pine",
      )}
    >
      {children}
    </button>
  );
}

function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  return (
    <nav aria-label="Phân trang sản phẩm" className="mt-14 flex items-center justify-center gap-2">
      <SiteButton
        variant="outline"
        size="sm"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        Trang trước
      </SiteButton>
      <span className="tnum px-3 text-[15px] text-stone">
        Trang {page + 1} trên {totalPages}
      </span>
      <SiteButton
        variant="outline"
        size="sm"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Trang sau
      </SiteButton>
    </nav>
  );
}

function ProductGridSkeleton() {
  return (
    <ul className="grid grid-cols-2 gap-x-6 gap-y-10 xl:grid-cols-3" aria-hidden>
      {Array.from({ length: 6 }).map((_, i) => (
        <li key={i}>
          <div className="arch aspect-[4/5] w-full animate-pulse bg-mint" />
          <div className="mt-4 space-y-2">
            <div className="h-4 w-1/2 animate-pulse rounded bg-mint" />
            <div className="h-5 w-3/4 animate-pulse rounded bg-mint" />
            <div className="h-5 w-1/3 animate-pulse rounded bg-mint" />
          </div>
        </li>
      ))}
    </ul>
  );
}
