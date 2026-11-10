"use client";

import { useState } from "react";
import Link from "next/link";
import { Mail, MapPin, Phone, Send } from "lucide-react";
import { BrandMark } from "./Brand";
import { CLINIC } from "@/config/clinic";

const QUICK_LINKS = [
  { href: "/", label: "Trang chủ" },
  { href: "/about", label: "Giới thiệu" },
  { href: "/services", label: "Dịch vụ" },
  { href: "/products", label: "Cửa hàng" },
  { href: "/appointments/create", label: "Đặt lịch khám" },
  { href: "/contact", label: "Liên hệ" },
];

const SUPPORT_LINKS = [
  { href: "/products", label: "Hướng dẫn mua hàng" },
  { href: "/cart", label: "Giỏ hàng" },
  { href: "/orders", label: "Đơn hàng của tôi" },
  { href: "/appointments", label: "Lịch khám của tôi" },
  { href: "/pets", label: "Thú cưng của tôi" },
];

/* Lucide bản này không còn icon thương hiệu nên vẽ tay cho gọn. */
const SOCIALS = [
  {
    label: "Facebook",
    icon: (
      <path d="M13.5 21v-7.5h2.5l.4-3h-2.9V8.6c0-.9.3-1.5 1.5-1.5h1.5V4.4c-.3 0-1.2-.1-2.2-.1-2.2 0-3.7 1.3-3.7 3.8v2.4H8v3h2.6V21h2.9Z" />
    ),
  },
  {
    label: "YouTube",
    icon: <path d="M21 8.2a2.6 2.6 0 0 0-1.8-1.8C17.6 6 12 6 12 6s-5.6 0-7.2.4A2.6 2.6 0 0 0 3 8.2 27 27 0 0 0 2.6 12c0 1.3.1 2.6.4 3.8a2.6 2.6 0 0 0 1.8 1.8C6.4 18 12 18 12 18s5.6 0 7.2-.4a2.6 2.6 0 0 0 1.8-1.8c.3-1.2.4-2.5.4-3.8s-.1-2.6-.4-3.8ZM10.2 14.5v-5l4.6 2.5-4.6 2.5Z" />,
  },
  {
    label: "TikTok",
    icon: <path d="M16.6 5.8A4.3 4.3 0 0 1 15.5 3h-3.1v12.4a2.6 2.6 0 1 1-2.6-2.6c.3 0 .5 0 .8.1V9.7a5.7 5.7 0 1 0 4.9 5.7V9.1a7.3 7.3 0 0 0 4.3 1.4V7.4a4.3 4.3 0 0 1-3.2-1.6Z" />,
  },
];

export function SiteFooter() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);

  return (
    <footer className="relative z-10 -mt-10 text-white md:-mt-14">
      {/* Mép sóng nối với phần phía trên */}
      <svg aria-hidden viewBox="0 0 1440 60" preserveAspectRatio="none" className="block h-10 w-full text-[#1f4a3c] md:h-14">
        <path fill="currentColor" d="M0 40C180 10 360 0 560 18s400 40 600 22 220-30 280-34V60H0Z" />
      </svg>

      <div className="bg-[#1f4a3c]">
        <div className="mx-auto w-full max-w-[1440px] px-5 pb-8 pt-8 md:px-6">
          <div className="grid gap-10 md:grid-cols-2 lg:grid-cols-[1.3fr_1fr_1fr_1.4fr] lg:gap-0 lg:[&>*+*]:border-l lg:[&>*+*]:border-white/20 lg:[&>*+*]:pl-10 lg:[&>*]:pr-8">
            <div>
              <div className="flex items-center gap-2.5">
                <BrandMark className="size-10" />
                <span className="text-[22px] font-extrabold">
                  Vet <span className="text-[#f57c1f]">Clinic</span>
                </span>
              </div>
              <p className="mt-4 max-w-xs text-sm leading-relaxed text-white/85">
                Hệ thống chăm sóc thú cưng uy tín, đồng hành cùng bạn và thú cưng.
              </p>
              <div className="mt-6 flex gap-3">
                {SOCIALS.map((s) => (
                  <a
                    key={s.label}
                    href="#"
                    aria-label={s.label}
                    className="grid size-8 place-items-center rounded-full bg-white text-[#1f4a3c] transition hover:bg-[#f57c1f] hover:text-white"
                  >
                    <svg viewBox="0 0 24 24" className="size-4" fill="currentColor">{s.icon}</svg>
                  </a>
                ))}
                <a
                  href="#"
                  aria-label="Zalo"
                  className="grid size-8 place-items-center rounded-full bg-white text-[9px] font-bold text-[#1f4a3c] transition hover:bg-[#f57c1f] hover:text-white"
                >
                  Zalo
                </a>
              </div>
            </div>

            <FooterCol title="Liên kết nhanh" links={QUICK_LINKS} />
            <FooterCol title="Hỗ trợ khách hàng" links={SUPPORT_LINKS} />

            <div>
              <h2 className="text-[15px] font-bold">Liên hệ với chúng tôi</h2>
              <ul className="mt-4 space-y-3 text-sm text-white/85">
                <li className="flex gap-3">
                  <Phone aria-hidden className="mt-0.5 size-4 shrink-0" />
                  <a href={`tel:${CLINIC.phone.replace(/\s/g, "")}`} className="hover:text-white">{CLINIC.phone}</a>
                </li>
                <li className="flex gap-3">
                  <Mail aria-hidden className="mt-0.5 size-4 shrink-0" />
                  support@vetclinic.vn
                </li>
                <li className="flex gap-3">
                  <MapPin aria-hidden className="mt-0.5 size-4 shrink-0" />
                  {CLINIC.address}
                </li>
              </ul>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (email.trim()) setSent(true);
                }}
                className="mt-5 flex max-w-sm items-center rounded-full bg-white p-1 pl-4"
              >
                <input
                  type="email"
                  value={email}
                  onChange={(e) => { setEmail(e.target.value); setSent(false); }}
                  placeholder="Đăng ký nhận tin"
                  aria-label="Email nhận tin"
                  className="min-w-0 flex-1 bg-transparent text-sm text-[#2b1d14] outline-none placeholder:text-[#6b5a4e]"
                />
                <button type="submit" aria-label="Đăng ký" className="grid h-9 w-11 place-items-center rounded-full bg-[#f57c1f] text-white">
                  <Send className="size-4" />
                </button>
              </form>
              {sent && <p className="mt-2 text-xs text-white/70">Cảm ơn bạn đã đăng ký!</p>}
            </div>
          </div>

          <div className="mt-10 border-t border-white/20 pt-5 text-xs text-white/75">
            © {new Date().getFullYear()} Vet Clinic. Tất cả quyền được bảo lưu.
          </div>
        </div>
      </div>
    </footer>
  );
}

function FooterCol({ title, links }: { title: string; links: Array<{ href: string; label: string }> }) {
  return (
    <div>
      <h2 className="text-[15px] font-bold">{title}</h2>
      <ul className="mt-4 space-y-2.5 text-sm">
        {links.map((l) => (
          <li key={l.label}>
            <Link href={l.href} className="text-white/85 transition-colors hover:text-[#f57c1f]">{l.label}</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
