import Link from "next/link";
import { Clock, MapPin, Phone } from "lucide-react";
import { BrandMark } from "./Brand";
import { CLINIC } from "@/config/clinic";

const COLUMNS: Array<{ title: string; links: Array<{ href: string; label: string }> }> = [
  {
    title: "Dịch vụ",
    links: [
      { href: "/#dich-vu", label: "Khám tổng quát" },
      { href: "/#dich-vu", label: "Tiêm phòng" },
      { href: "/#dich-vu", label: "Phẫu thuật" },
      { href: "/#dich-vu", label: "Chăm sóc và làm đẹp" },
    ],
  },
  {
    title: "Mua sắm",
    links: [
      { href: "/products", label: "Tất cả sản phẩm" },
      { href: "/cart", label: "Giỏ hàng" },
      { href: "/orders", label: "Đơn hàng của tôi" },
    ],
  },
  {
    title: "Tài khoản",
    links: [
      { href: "/appointments/create", label: "Đặt lịch khám" },
      { href: "/pets", label: "Thú cưng của tôi" },
      { href: "/appointments", label: "Lịch khám của tôi" },
      { href: "/login", label: "Đăng nhập" },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="bg-pine text-white">
      <div className="mx-auto w-full max-w-[1200px] px-5 py-16 md:px-6 md:py-20">
        <div className="grid gap-12 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
          <div>
            <div className="flex items-center gap-2.5">
              <BrandMark className="size-10" />
              <span className="font-[family-name:var(--font-brand)] text-[21px] font-semibold">
                Vet Clinic
              </span>
            </div>
            <p className="measure mt-4 text-white/70">
              Phòng khám thú y chăm sóc chó mèo và thú cưng nhỏ. Khám, tiêm phòng, phẫu thuật và
              theo dõi sức khoẻ lâu dài cho bé nhà bạn.
            </p>

            <ul className="mt-6 space-y-3 text-[15px] text-white/80">
              <li className="flex gap-3">
                <MapPin aria-hidden className="mt-0.5 size-4 shrink-0 text-teal" />
                {CLINIC.address}
              </li>
              <li className="flex gap-3">
                <Phone aria-hidden className="mt-0.5 size-4 shrink-0 text-teal" />
                <a href={`tel:${CLINIC.phone.replace(/\s/g, "")}`} className="hover:text-white">
                  {CLINIC.phone}
                </a>
              </li>
              <li className="flex gap-3">
                <Clock aria-hidden className="mt-0.5 size-4 shrink-0 text-teal" />
                {CLINIC.hours}
              </li>
            </ul>
          </div>

          {COLUMNS.map((col) => (
            <div key={col.title}>
              <h2 className="font-[family-name:var(--font-brand)] text-[17px] font-semibold">
                {col.title}
              </h2>
              <ul className="mt-4 space-y-2.5">
                {col.links.map((l) => (
                  <li key={l.label}>
                    <Link href={l.href} className="text-white/70 transition-colors hover:text-white">
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-14 flex flex-wrap items-center gap-x-8 gap-y-3 border-t border-white/15 pt-8 text-sm text-white/55">
          <p>© {new Date().getFullYear()} Vet Clinic</p>
          <p>Đồ án hệ thống quản lý phòng khám thú y</p>
        </div>
      </div>
    </footer>
  );
}
