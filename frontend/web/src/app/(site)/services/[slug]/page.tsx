"use client";

/*
  Chi tiết một dịch vụ — dựng theo ảnh mẫu (skill image-to-ui): nền kem họa tiết dấu chân,
  hero (breadcrumb dạng chip, tiêu đề 2 dòng, chip giá/thời lượng, chip điểm nổi bật, nút đặt
  lịch, ảnh trên mảng cam), hộp giới thiệu 2 cột, "Khi nào cần…" + 4 bước quy trình xen kẽ
  cam/xanh, mục Chi phí dạng accordion, cột phải: thẻ đặt lịch xanh + "Dịch vụ khác".
  Thông tin dịch vụ (tên, giá, thời lượng) lấy từ booking-service; bài viết, quy trình, FAQ
  hiện chỉ có ở nội dung mẫu (MOCK-TẠM) vì backend chưa lưu.
*/

import Link from "next/link";
import { use } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  Clock,
  Heart,
  PawPrint,
  Phone,
  ShieldCheck,
  Tag,
} from "lucide-react";
import { extraServiceArticles, serviceExtra } from "@/lib/content/service-content"; // MOCK-TẠM
import { clinicServiceApi } from "@/lib/api";
import { mockServiceDetails, type ArticleBlock } from "@/lib/api/mock-data"; // MOCK-TẠM
import { formatPrice } from "@/lib/utils/format";
import { CLINIC } from "@/config/clinic";
import { Paw } from "@/components/site/Decor";

const ORANGE = "#f57c1f";
const GREEN = "#1f4a3c";
const FALLBACK_IMG = "/img/cham-soc.jpg";

/** Họa tiết dấu chân lặp lại làm nền trang (SVG nhúng, màu cam rất nhạt). */
const PAW_PATTERN =
  "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='140' height='140' viewBox='0 0 140 140'%3E%3Cg fill='%23f9c79a' fill-opacity='0.35'%3E%3Cg transform='translate(18 20) rotate(-15)'%3E%3Cellipse cx='12' cy='16' rx='7' ry='6'/%3E%3Ccircle cx='3' cy='7' r='3'/%3E%3Ccircle cx='9' cy='2' r='3'/%3E%3Ccircle cx='16' cy='2' r='3'/%3E%3Ccircle cx='21' cy='7' r='3'/%3E%3C/g%3E%3Cg transform='translate(88 86) rotate(20)'%3E%3Cellipse cx='12' cy='16' rx='7' ry='6'/%3E%3Ccircle cx='3' cy='7' r='3'/%3E%3Ccircle cx='9' cy='2' r='3'/%3E%3Ccircle cx='16' cy='2' r='3'/%3E%3Ccircle cx='21' cy='7' r='3'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E\")";

interface Section {
  title: string;
  items: string[];
  paras: string[];
  imgs: string[];
}

/** Tách bài viết thành: đoạn mở đầu + các mục theo tiêu đề h2/h3. */
function parseArticle(blocks: ArticleBlock[]) {
  const intro: string[] = [];
  const sections: Section[] = [];
  let cur: Section | null = null;
  for (const [tag, v] of blocks) {
    if (tag === "h2" || tag === "h3") {
      cur = { title: v, items: [], paras: [], imgs: [] };
      sections.push(cur);
    } else if (!cur) {
      if (tag === "p") intro.push(v);
    } else if (tag === "li") cur.items.push(v);
    else if (tag === "img") cur.imgs.push(v);
    else if (tag === "h4") cur.paras.push(`**${v}`);
    else cur.paras.push(v);
  }
  return { intro, sections };
}

export default function ServiceDetailPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const services = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list });
  const service = services.data?.find((s) => s.slug === slug);
  const article = mockServiceDetails[slug] ?? extraServiceArticles[slug];
  const extra = serviceExtra(slug);
  const others = (services.data ?? []).filter((s) => s.active && s.slug !== slug).slice(0, 6);

  if (services.isLoading) {
    return <div className="mx-auto mt-4 h-96 max-w-[1440px] animate-pulse rounded-[32px] bg-[#fff3e8]" />;
  }
  if (!service) {
    return (
      <div className="mx-auto max-w-xl px-6 py-24 text-center">
        <p className="text-2xl font-extrabold text-[#2b1d14]">Không tìm thấy dịch vụ</p>
        <Link href="/services" className="mt-6 inline-flex items-center gap-2 font-semibold" style={{ color: ORANGE }}>
          <ArrowLeft className="size-4" /> Về danh sách dịch vụ
        </Link>
      </div>
    );
  }

  const heroImg = article?.blocks.find((b) => b[0] === "img")?.[1] ?? FALLBACK_IMG;
  const { intro, sections } = parseArticle(article?.blocks.slice(1) ?? []);
  const booking = `/appointments/create?service=${service.slug}`;

  // Tiêu đề 2 dòng: phần trước "—" to, phần sau nhỏ hơn (như ảnh mẫu).
  const fullTitle = article?.title ?? service.name;
  const [titleMain, titleSub] = fullTitle.split(/\s+[—–-]\s+/, 2);

  // Mục "Chi phí" đưa xuống cạnh FAQ; mục danh sách đầu tiên làm "Khi nào cần…".
  const costSection = sections.find((s) => /chi phí|giá/i.test(s.title));
  const rest = sections.filter((s) => s !== costSection);
  const checklist = rest.find((s) => s.items.length > 0);
  const afterChecklist = rest.filter((s) => s !== checklist);
  // Mục ngắn đầu tiên (không ảnh, ≤ 2 đoạn) đặt dưới checklist như ảnh mẫu; còn lại xuống dưới.
  const sideNote = afterChecklist.find((s) => s.imgs.length === 0 && s.items.length === 0 && s.paras.length <= 2);
  const longSections = afterChecklist.filter((s) => s !== sideNote);

  const introLeft = intro.join(" ") || service.description || "";
  const introRight = sideNote?.paras.join(" ") ?? service.description ?? "";

  return (
    <div className="relative pb-16" style={{ backgroundColor: "#fff8f1", backgroundImage: PAW_PATTERN }}>
      <div className="mx-auto max-w-[1440px] px-4 pt-4 sm:px-6">
        {/* ---------- Hero ---------- */}
        <section className="relative overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_0%_0%,#fff9f2_0%,#ffeedd_55%,#ffe0c2_100%)] px-6 py-10 shadow-[0_10px_40px_-24px_rgba(160,80,20,0.45)] sm:px-10 md:py-12">
          <Paw className="left-[50%] top-8 size-10 -rotate-12 text-[#f9a55c]/80" />
          <Paw className="bottom-6 right-6 size-14 rotate-12 text-[#f9a55c]" />
          <Paw className="bottom-10 left-[48%] hidden size-6 rotate-45 text-[#fbb983] md:block" />
          <Heart aria-hidden className="absolute right-8 top-8 size-9 -rotate-12" strokeWidth={2} style={{ color: ORANGE }} />
          <Heart aria-hidden className="absolute right-[24%] top-10 hidden size-6 fill-current md:block" style={{ color: "#f9a55c" }} />

          <div className="relative grid items-center gap-10 lg:grid-cols-[1.25fr_1fr]">
            <div>
              <nav className="inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-medium text-white" style={{ background: ORANGE }}>
                <Clock className="size-4" />
                <Link href="/services" className="hover:underline">Dịch vụ</Link>
                <span>/</span>
                <span>{service.name}</span>
              </nav>

              <h1 className="mt-6 font-extrabold leading-[1.1]" style={{ color: GREEN }}>
                <span className="block text-[38px] sm:text-[48px]">
                  {titleMain}
                  {titleSub && " –"}
                </span>
                {titleSub && <span className="mt-1 block text-[28px] sm:text-[36px]">{titleSub}</span>}
              </h1>
              {service.description && <p className="mt-4 max-w-xl text-[16px] font-medium text-[#2b1d14]">{service.description}</p>}

              <div className="mt-6 flex flex-wrap gap-3">
                {service.referencePrice != null && (
                  <span className="inline-flex items-center gap-2 rounded-full bg-[#ffc996] px-5 py-2.5 text-[15px] font-semibold text-[#2b1d14]">
                    <Clock className="size-5" style={{ color: ORANGE }} /> Từ {formatPrice(service.referencePrice)}
                  </span>
                )}
                <span className="inline-flex items-center gap-2 rounded-full bg-[#cfe6c4] px-5 py-2.5 text-[15px] font-semibold text-[#2b1d14]">
                  <Clock className="size-5" style={{ color: GREEN }} /> Khoảng {service.durationMinutes} phút
                </span>
              </div>

              <ul className="mt-5 flex flex-wrap gap-2">
                {extra.highlights.map((h, i) => {
                  const Icon = [Clock, CheckCircle2, ShieldCheck][i % 3];
                  return (
                    <li
                      key={h}
                      className="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[13px] font-medium text-[#2b1d14]"
                      style={{ background: i % 2 === 0 ? "#d9ecd0" : "#ffe1c4" }}
                    >
                      <span className="grid size-5 place-items-center rounded-full text-white" style={{ background: ORANGE }}>
                        <Icon className="size-3.5" />
                      </span>
                      {h}
                    </li>
                  );
                })}
              </ul>

              <Link
                href={booking}
                className="mt-8 inline-flex h-14 items-center gap-2.5 rounded-full bg-gradient-to-r from-[#ff8a2a] to-[#f2661b] px-8 text-[17px] font-bold text-white shadow-[0_12px_24px_-10px_rgba(245,124,31,0.9)] transition hover:brightness-110"
              >
                <CalendarDays className="size-5" /> Đặt lịch dịch vụ ngay <ArrowRight className="size-5" />
              </Link>
            </div>

            <div className="relative mx-auto w-full max-w-md">
              <div aria-hidden className="absolute -bottom-6 -left-6 -right-2 top-10 rounded-[58%_42%_55%_45%/48%_60%_40%_52%] bg-[linear-gradient(135deg,#ffcf9f,#f7a65a)] opacity-70" />
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={heroImg} alt={service.name} className="relative aspect-[4/3] w-full rounded-[28px] object-cover shadow-xl ring-4 ring-white/70" />
            </div>
          </div>
        </section>

        {/* ---------- Hộp giới thiệu 2 cột ---------- */}
        {(introLeft || introRight) && (
          <section className="mt-8 grid gap-6 rounded-[28px] bg-[#fff1e3]/90 px-6 py-6 text-[15px] leading-relaxed text-[#2b1d14] md:grid-cols-2 md:divide-x md:divide-[#f3d5b5] md:px-10">
            <p>{introLeft}</p>
            {introRight && introRight !== introLeft && <p className="md:pl-8">{introRight}</p>}
          </section>
        )}

        {/* ---------- Nội dung + cột phải ---------- */}
        <div className="mt-10 grid gap-8 lg:grid-cols-[1fr_280px]">
          <div className="min-w-0">
            <div className="grid gap-6 md:grid-cols-[1fr_1.1fr]">
              <div>
                {checklist && (
                  <>
                    <SectionTitle>{checklist.title}</SectionTitle>
                    <ul className="mt-4 space-y-2.5">
                      {checklist.items.map((it) => (
                        <li key={it} className="flex gap-2.5 text-[14px] leading-snug text-[#2b1d14]">
                          <CheckCircle2 className="mt-0.5 size-[18px] shrink-0 fill-[#f57c1f] text-white" />
                          {it}
                        </li>
                      ))}
                    </ul>
                    {checklist.paras.map((p) => (
                      <p key={p} className="mt-3 text-[14px] leading-relaxed text-[#3b2a20]">{p}</p>
                    ))}
                  </>
                )}
                {sideNote && (
                  <div className={checklist ? "mt-7" : ""}>
                    <SectionTitle>{sideNote.title}</SectionTitle>
                    {sideNote.paras.map((p) => (
                      <p key={p} className="mt-3 text-[14px] leading-relaxed text-[#3b2a20]">{p}</p>
                    ))}
                  </div>
                )}
              </div>

              <ol className="grid grid-cols-2 gap-4 self-start">
                {extra.steps.map((st, i) => {
                  const orange = i % 2 === 0;
                  return (
                    <li key={st.title} className="rounded-3xl p-4 shadow-sm" style={{ background: orange ? "#ffc58f" : "#c9e3bd" }}>
                      <span className="grid size-9 place-items-center rounded-full text-sm font-bold text-white" style={{ background: orange ? ORANGE : GREEN }}>
                        {i + 1}
                      </span>
                      <p className="mt-3 font-bold leading-snug text-[#2b1d14]">{st.title}</p>
                      <p className="mt-1 text-[13px] leading-relaxed text-[#3b2a20]">{st.text}</p>
                    </li>
                  );
                })}
              </ol>
            </div>

            {/* Các mục dài của bài viết (nếu có) */}
            {longSections.map((s) => (
              <div key={s.title} className="mt-10">
                <SectionTitle>{s.title}</SectionTitle>
                {s.paras.map((p, i) =>
                  p.startsWith("**") ? (
                    <h3 key={i} className="mt-5 text-lg font-bold" style={{ color: ORANGE }}>{p.slice(2)}</h3>
                  ) : (
                    <p key={i} className="mt-3 text-[15px] leading-relaxed text-[#3b2a20]">{p}</p>
                  ),
                )}
                {s.items.length > 0 && (
                  <ul className="mt-4 space-y-2">
                    {s.items.map((it) => (
                      <li key={it} className="flex gap-2.5 text-[15px] leading-relaxed text-[#3b2a20]">
                        <PawPrint className="mt-1 size-4 shrink-0" style={{ color: ORANGE }} /> {it}
                      </li>
                    ))}
                  </ul>
                )}
                {s.imgs.map((src) => (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img key={src} src={src} alt="" loading="lazy" className="mx-auto my-6 h-auto w-auto max-w-full rounded-3xl shadow-md" />
                ))}
              </div>
            ))}

            {/* ---------- Chi phí + FAQ ---------- */}
            <div className="mt-10">
              <h2 className="flex items-center gap-2.5 text-2xl font-extrabold" style={{ color: GREEN }}>
                <Tag className="size-6 -rotate-45 fill-current" style={{ color: ORANGE }} /> Chi phí
              </h2>
              {costSection?.paras.map((p) => (
                <p key={p} className="mt-3 text-[15px] leading-relaxed text-[#3b2a20]">{p}</p>
              ))}
              {!costSection && service.referencePrice != null && (
                <p className="mt-3 text-[15px] leading-relaxed text-[#3b2a20]">
                  Giá tham khảo từ {formatPrice(service.referencePrice)}. Bác sĩ sẽ báo chi phí cụ thể trước khi thực hiện.
                </p>
              )}
              <div className="mt-5 space-y-3">
                {extra.faqs.map((f) => (
                  <details key={f.q} className="group rounded-2xl bg-[#fff1e3] px-5 py-4 shadow-[0_2px_10px_-6px_rgba(160,80,20,0.4)] open:bg-white">
                    <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold text-[#2b1d14]">
                      {f.q}
                      <ChevronDown className="size-5 shrink-0 transition group-open:rotate-180" />
                    </summary>
                    <p className="mt-3 text-[15px] leading-relaxed text-[#3b2a20]">{f.a}</p>
                  </details>
                ))}
              </div>
            </div>
          </div>

          {/* ---------- Cột phải ---------- */}
          <aside className="space-y-6 lg:sticky lg:top-24 lg:self-start">
            <div className="rounded-3xl p-6 text-white shadow-xl" style={{ background: GREEN }}>
              <p className="text-xl font-extrabold leading-snug">Đặt lịch {service.name.toLowerCase()}</p>
              <p className="mt-2 text-sm text-white/85">Chọn gói phù hợp, bác sĩ sẽ chuẩn bị trước khi bạn tới</p>
              <Link
                href={booking}
                className="mt-5 flex h-12 items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff8a2a] to-[#f2661b] text-[15px] font-bold shadow-[0_8px_18px_-8px_rgba(245,124,31,0.9)]"
              >
                Đặt lịch online <ArrowRight className="size-4" />
              </Link>
              <a href={`tel:${CLINIC.phone.replace(/\s/g, "")}`} className="mt-4 flex items-center justify-center gap-2 font-semibold">
                <Phone className="size-4" /> {CLINIC.phone}
              </a>
            </div>

            <div className="rounded-3xl bg-white/90 p-5 shadow-[0_4px_24px_-10px_rgba(160,80,20,0.35)]">
              <p className="mb-3 flex items-center gap-2 text-lg font-extrabold" style={{ color: GREEN }}>
                <PawPrint className="size-5 fill-current" style={{ color: ORANGE }} /> Dịch vụ khác
              </p>
              <ul className="space-y-0.5">
                {others.map((s) => (
                  <li key={s.id}>
                    <Link href={`/services/${s.slug}`} className="flex items-center gap-2 rounded-xl px-2 py-2 text-sm text-[#2b1d14] hover:bg-[#fff3e8] hover:text-[#f57c1f]">
                      <PawPrint className="size-3.5 shrink-0 fill-current" style={{ color: "#f9a55c" }} />
                      {s.name}
                      <ArrowRight className="size-3.5" />
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="flex items-start gap-2.5 text-2xl font-extrabold leading-snug" style={{ color: GREEN }}>
      <PawPrint className="mt-0.5 size-6 shrink-0 fill-current" style={{ color: ORANGE }} />
      {children}
    </h2>
  );
}
