/*
  Trang liên hệ — bố cục theo ảnh mẫu (đầu trang có nút gọi / Zalo, hai cột: thông tin liên hệ
  và bản đồ Google), phong cách cam – xanh rêu của site. Số điện thoại, địa chỉ lấy từ
  config/clinic; email, Zalo, Facebook là thông tin tạm.
*/

import Link from "next/link";
import { ArrowUpRight, Clock, Mail, MapPin, MessageCircle, Phone } from "lucide-react";
import { CLINIC } from "@/config/clinic";
import { Leaf, Paw } from "@/components/site/Decor";

const ORANGE = "#f57c1f";
const GREEN = "#1f4a3c";
const EMAIL = "support@vetclinic.vn";
const ZALO = "https://zalo.me/02923838686";
const FACEBOOK = "https://facebook.com/";
const TEL = `tel:${CLINIC.phone.replace(/\s/g, "")}`;
const MAP_QUERY = encodeURIComponent(CLINIC.address);

export const metadata = { title: "Liên hệ | Vet Clinic" };

export default function ContactPage() {
  return (
    <div className="bg-white pb-16">
      {/* ---------- Đầu trang ---------- */}
      <div className="mx-auto max-w-[1440px] px-4 pt-4 sm:px-6">
        <section className="relative overflow-hidden rounded-[32px] bg-[radial-gradient(120%_140%_at_0%_0%,#fffaf4_0%,#ffeedd_45%,#ffd9b3_100%)] px-6 py-12 sm:px-12 md:py-16">
          <div
            aria-hidden
            className="absolute inset-0 opacity-50"
            style={{ backgroundImage: "radial-gradient(#f7b37a 1.2px, transparent 1.4px)", backgroundSize: "22px 22px", maskImage: "linear-gradient(100deg, transparent 30%, #000 100%)", WebkitMaskImage: "linear-gradient(100deg, transparent 30%, #000 100%)" }}
          />
          <Paw className="right-[10%] top-10 size-10 rotate-12 text-[#f9a55c]" />
          <Paw className="bottom-10 right-[38%] hidden size-7 -rotate-12 text-[#fbb983] md:block" />
          <Leaf className="-bottom-4 right-6 h-28 w-12 rotate-[35deg] bg-[linear-gradient(135deg,#7cc36b,#3f8f3a)]" />

          <div className="relative grid items-end gap-8 md:grid-cols-[1.3fr_1fr]">
            <div>
              <Breadcrumb current="Liên hệ" />
              <h1 className="mt-5 text-[44px] font-extrabold leading-none sm:text-[64px]" style={{ color: GREEN }}>
                Liên hệ<span style={{ color: ORANGE }}>.</span>
              </h1>
              <p className="mt-5 max-w-xl text-[16px] leading-relaxed text-[#3b2a20]">
                Vet Clinic luôn sẵn sàng lắng nghe. Gọi hotline, nhắn Zalo hoặc ghé phòng khám để được bác sĩ tư vấn
                miễn phí về sức khoẻ, dinh dưỡng và lịch tiêm phòng cho bé.
              </p>
            </div>
            <div className="flex flex-wrap gap-3 md:justify-end">
              <a href={TEL} className="inline-flex h-12 items-center gap-2 rounded-full px-6 text-sm font-semibold text-white shadow-[0_8px_18px_-8px_rgba(245,124,31,0.9)]" style={{ background: ORANGE }}>
                <Phone className="size-4" /> Gọi ngay · {CLINIC.phone}
              </a>
              <a href={ZALO} target="_blank" rel="noreferrer" className="inline-flex h-12 items-center gap-2 rounded-full border-2 bg-white/70 px-6 text-sm font-semibold" style={{ borderColor: GREEN, color: GREEN }}>
                <MessageCircle className="size-4" /> Nhắn Zalo
              </a>
            </div>
          </div>
        </section>
      </div>

      {/* ---------- Thông tin + bản đồ ---------- */}
      <div className="mx-auto grid max-w-[1200px] gap-12 px-4 pt-14 sm:px-6 lg:grid-cols-[1fr_1.15fr]">
        <section>
          <Eyebrow>Thông tin liên hệ</Eyebrow>
          <h2 className="mt-3 text-[34px] font-extrabold leading-tight sm:text-[42px]" style={{ color: GREEN }}>Vet Clinic</h2>

          <dl className="mt-8 divide-y divide-[#f1e6dc] border-y border-[#f1e6dc]">
            <InfoRow icon={MapPin} label="Địa chỉ phòng khám">
              <p>{CLINIC.address}</p>
              <a
                href={`https://www.google.com/maps/dir/?api=1&destination=${MAP_QUERY}`}
                target="_blank"
                rel="noreferrer"
                className="mt-1 inline-flex items-center gap-1 text-sm font-semibold underline underline-offset-4"
                style={{ color: ORANGE }}
              >
                Chỉ đường <ArrowUpRight className="size-3.5" />
              </a>
            </InfoRow>
            <InfoRow icon={Phone} label="Hotline">
              <a href={TEL} className="text-2xl font-extrabold" style={{ color: GREEN }}>{CLINIC.phone}</a>
            </InfoRow>
            <InfoRow icon={Mail} label="Email">
              <a href={`mailto:${EMAIL}`} className="hover:underline">{EMAIL}</a>
            </InfoRow>
            <InfoRow icon={Clock} label="Giờ mở cửa">
              <p>{CLINIC.hours}</p>
              <p className="text-sm text-[#6b5a4e]">Cấp cứu 24/7 qua hotline</p>
            </InfoRow>
            <InfoRow icon={MessageCircle} label="Mạng xã hội">
              <div className="flex gap-6">
                <a href={ZALO} target="_blank" rel="noreferrer" className="font-semibold hover:underline">Zalo</a>
                <a href={FACEBOOK} target="_blank" rel="noreferrer" className="font-semibold hover:underline">Facebook</a>
              </div>
            </InfoRow>
          </dl>

          <Link
            href="/appointments/create"
            className="mt-8 inline-flex h-12 items-center gap-2 rounded-full px-6 text-sm font-semibold text-white"
            style={{ background: GREEN }}
          >
            Đặt lịch khám online <ArrowUpRight className="size-4" />
          </Link>
        </section>

        <section>
          <Eyebrow>Bản đồ</Eyebrow>
          <h2 className="mt-3 text-[34px] font-extrabold leading-tight sm:text-[42px]" style={{ color: GREEN }}>Google Maps</h2>
          <div className="relative mt-8">
            <div aria-hidden className="absolute -inset-3 rounded-[36px] bg-[linear-gradient(135deg,#ffcf9f,#f7a65a)] opacity-60" />
            <iframe
              title="Bản đồ Vet Clinic"
              src={`https://www.google.com/maps?q=${MAP_QUERY}&output=embed`}
              loading="lazy"
              referrerPolicy="no-referrer-when-downgrade"
              className="relative aspect-square w-full rounded-[28px] border-4 border-white shadow-xl sm:aspect-[4/3.6]"
            />
          </div>
        </section>
      </div>
    </div>
  );
}

function Breadcrumb({ current }: { current: string }) {
  return (
    <nav className="flex items-center gap-2 text-sm text-[#6b5a4e]">
      <Link href="/" className="hover:underline">Trang chủ</Link>
      <span>/</span>
      <span className="font-semibold" style={{ color: ORANGE }}>{current}</span>
    </nav>
  );
}

function Eyebrow({ children }: { children: React.ReactNode }) {
  return (
    <p className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.2em]" style={{ color: ORANGE }}>
      <span className="h-px w-8" style={{ background: ORANGE }} /> {children}
    </p>
  );
}

function InfoRow({ icon: Icon, label, children }: { icon: React.ComponentType<{ className?: string }>; label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-4 py-6">
      <span className="grid size-11 shrink-0 place-items-center rounded-2xl bg-[#fff1e4]" style={{ color: ORANGE }}>
        <Icon className="size-5" />
      </span>
      <div className="text-[16px] text-[#2b1d14]">
        <dt className="mb-1 text-xs font-semibold uppercase tracking-[0.15em] text-[#9a8b80]">{label}</dt>
        <dd>{children}</dd>
      </div>
    </div>
  );
}
