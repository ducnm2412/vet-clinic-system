"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ChevronDown, KeyRound, LayoutGrid, LogOut, Menu, PawPrint, Search, ShoppingBag, User, UserRound, X } from "lucide-react";
import { CustomerAvatar } from "@/components/user/UserSidebar";
import { clinicServiceApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useCart } from "@/lib/useCart";
import { cn } from "@/lib/utils/cn";
import { BrandLockup } from "./Brand";
import { ButtonLink } from "./primitives";

type NavLink = { href: string; label: string };

/** Khách vãng lai: các trang giới thiệu chung. */
const GUEST_LINKS: NavLink[] = [
  { href: "/", label: "Trang chủ" },
  { href: "/about", label: "Giới thiệu" },
  { href: "/services", label: "Dịch vụ" },
  { href: "/products", label: "Sản phẩm" },
  { href: "/contact", label: "Liên hệ" },
];

/** Khách hàng đã đăng nhập: các mục riêng (thú cưng, lịch hẹn, đơn hàng…) nằm ở sidebar /user. */
const CUSTOMER_LINKS: NavLink[] = [
  { href: "/", label: "Trang chủ" },
  { href: "/services", label: "Dịch vụ" },
  { href: "/products", label: "Sản phẩm" },
];

export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { email, hasRole, signOut } = useAuth();
  const { itemCount, enabled: hasCart } = useCart();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  const isCustomer = hasRole("CUSTOMER");
  // Danh sách dịch vụ cho dropdown "Dịch vụ" — dùng chung cache với trang dịch vụ.
  const servicesQuery = useQuery({ queryKey: ["clinic-services"], queryFn: clinicServiceApi.list, staleTime: 5 * 60_000 });
  const services = (servicesQuery.data ?? [])
    .filter((s) => s.active)
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .map((s) => ({ href: `/services/${s.slug}`, label: s.name }));
  const links = isCustomer ? CUSTOMER_LINKS : GUEST_LINKS;

  // Đầu trang chỉ hiện đường kẻ sau khi người ta bắt đầu cuộn — lúc mới mở, hero liền
  // mạch với đầu trang, không bị một vạch cắt ngang.
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  // Mở ngăn kéo thì khoá cuộn nền, không thì nền trôi sau lưng menu.
  useEffect(() => {
    if (!drawerOpen) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [drawerOpen]);

  function isActive(href: string) {
    if (href === "/") return pathname === "/";
    if (href.startsWith("/#")) return false;
    return pathname === href || pathname.startsWith(`${href}/`);
  }

  return (
    <header
      className={cn(
        "top-0 z-50 transition-[background-color,box-shadow,backdrop-filter] duration-300",
        // Trang chủ: header nằm đè lên hero (trong suốt, chữ trắng), cuộn xuống mới hiện nền trắng.
        pathname === "/" ? "fixed inset-x-0" : "sticky",
        pathname === "/" && !scrolled && !drawerOpen
          ? "header-overlay bg-transparent"
          : "bg-white/95 backdrop-blur",
        scrolled ? "header-solid shadow-[0_1px_0_var(--mist),0_6px_20px_-18px_rgb(19_78_74/0.5)]" : "",
      )}
    >
      <div className="mx-auto flex h-18 w-full max-w-[1440px] items-center gap-4 px-5 md:px-6">
        <BrandLockup />

        <nav aria-label="Điều hướng chính" className="ml-2 hidden min-w-0 xl:block">
          <ul className="flex items-center gap-0.5">
            {links.map((l) =>
              l.href === "/services" ? (
                <ServicesDropdown key={l.href} active={isActive(l.href)} items={services} />
              ) : (
                <li key={l.href}>
                  <Link
                    href={l.href}
                    aria-current={isActive(l.href) ? "page" : undefined}
                    className={cn(
                      "whitespace-nowrap rounded-full px-3 py-2 text-[15px] transition-colors",
                      isActive(l.href)
                        ? "bg-mint font-medium text-pine"
                        : "text-stone hover:bg-mint hover:text-pine",
                    )}
                  >
                    {l.label}
                  </Link>
                </li>
              ),
            )}
          </ul>
        </nav>

        <div className="ml-auto flex items-center gap-2">
          {isCustomer && <HeaderSearch />}
          {hasCart && (
            <Link
              href="/cart"
              className="relative grid size-11 place-items-center rounded-full text-pine transition-colors hover:bg-mint"
              aria-label={itemCount > 0 ? `Giỏ hàng, ${itemCount} món` : "Giỏ hàng, đang trống"}
            >
              <ShoppingBag aria-hidden className="size-5" />
              {itemCount > 0 && (
                <span className="tnum absolute right-1 top-1 grid min-w-5 place-items-center rounded-full bg-coral-deep px-1 text-[11px] font-semibold leading-5 text-white">
                  {itemCount}
                </span>
              )}
            </Link>
          )}

          {email ? (
            <div className="relative hidden sm:block">
              {/*
                Chỉ biểu tượng, không kèm email: thanh này còn phải chứa menu, giỏ hàng và
                nút đặt lịch, mà một địa chỉ email dài đẩy hết mọi thứ khác vỡ hàng.
                Email vẫn nằm trong menu xổ xuống.
              */}
              <button
                onClick={() => setAccountOpen((v) => !v)}
                aria-expanded={accountOpen}
                aria-haspopup="menu"
                aria-label={`Tài khoản ${email}`}
                className={cn(
                  "flex items-center gap-1 rounded-full border border-mist text-pine transition-colors hover:border-[#f57c1f]",
                  isCustomer ? "p-1 pr-2" : "size-11 justify-center hover:bg-mint",
                )}
              >
                {isCustomer ? (
                  <>
                    <CustomerAvatar className="size-9" />
                    <ChevronDown aria-hidden className={cn("size-4 text-stone transition-transform", accountOpen && "rotate-180")} />
                  </>
                ) : (
                  <User aria-hidden className="size-5 text-teal" />
                )}
              </button>

              {accountOpen && (
                <>
                  <button
                    aria-label="Đóng menu tài khoản"
                    className="fixed inset-0 z-10 cursor-default"
                    onClick={() => setAccountOpen(false)}
                  />
                  <div
                    role="menu"
                    className="absolute right-0 z-20 mt-2 w-64 origin-top-right animate-[dropIn_.18s_ease-out] overflow-hidden rounded-2xl border border-mist bg-white py-1 shadow-[var(--shadow-lift)]"
                  >
                    <p className="truncate px-4 pb-2 pt-3 text-sm text-stone">{email}</p>
                    <hr className="mb-1 border-mist" />
                    {isCustomer && (
                      <>
                        <AccountItem href="/user" onClick={() => setAccountOpen(false)}>
                          <LayoutGrid aria-hidden className="size-4 text-stone" /> Tài khoản của tôi
                        </AccountItem>
                        <AccountItem href="/user/settings" onClick={() => setAccountOpen(false)}>
                          <UserRound aria-hidden className="size-4 text-stone" /> Thông tin cá nhân
                        </AccountItem>
                        <AccountItem href="/user/settings?tab=password" onClick={() => setAccountOpen(false)}>
                          <KeyRound aria-hidden className="size-4 text-stone" /> Đổi mật khẩu
                        </AccountItem>
                        <hr className="my-1 border-mist" />
                      </>
                    )}
                    {!isCustomer && (
                      <AccountItem href="/" onClick={() => setAccountOpen(false)}>
                        Về khu làm việc
                      </AccountItem>
                    )}
                    <button
                      role="menuitem"
                      onClick={() => {
                        signOut();
                        setAccountOpen(false);
                        router.push("/");
                      }}
                      className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-[15px] text-pine hover:bg-mint"
                    >
                      <LogOut aria-hidden className="size-4 text-stone" />
                      Đăng xuất
                    </button>
                  </div>
                </>
              )}
            </div>
          ) : (
            <Link
              href="/login"
              className="hidden h-11 items-center rounded-full px-4 text-[15px] text-pine transition-colors hover:bg-mint sm:inline-flex"
            >
              Đăng nhập
            </Link>
          )}

          {/*
            Nút đặt lịch có mặt ở mọi khổ màn hình, kể cả điện thoại: đó là việc cả trang
            này tồn tại để mời người ta làm.
          */}
          <ButtonLink href="/appointments/create" size="sm">
            Đặt lịch khám
          </ButtonLink>

          <button
            onClick={() => setDrawerOpen(true)}
            aria-label="Mở menu"
            className="grid size-11 place-items-center rounded-full text-pine transition-colors hover:bg-mint xl:hidden"
          >
            <Menu aria-hidden className="size-5" />
          </button>
        </div>
      </div>

      {drawerOpen && (
        <MobileDrawer
          links={links}
          services={services}
          email={email}
          isCustomer={isCustomer}
          onClose={() => setDrawerOpen(false)}
          onSignOut={() => {
            signOut();
            setDrawerOpen(false);
            router.push("/");
          }}
        />
      )}
    </header>
  );
}

function AccountItem({
  href,
  onClick,
  children,
}: {
  href: string;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <Link
      role="menuitem"
      href={href}
      onClick={onClick}
      className="flex items-center gap-2.5 px-4 py-2.5 text-[15px] text-pine hover:bg-mint"
    >
      {children}
    </Link>
  );
}

/**
 * Ngăn kéo trên điện thoại: trượt từ phải, mỗi mục cao 56px để ngón tay bấm không trượt.
 * Không phải bản thu nhỏ của thanh điều hướng máy để bàn.
 */
/**
 * Dropdown "Dịch vụ" trên máy để bàn: mở khi rê chuột hoặc khi focus bằng bàn phím
 * (focus-within), không cần state. Bấm chữ "Dịch vụ" vẫn đi tới trang danh sách.
 */
function ServicesDropdown({ active, items }: { active: boolean; items: Array<{ href: string; label: string }> }) {
  return (
    <li className="group relative">
      <Link
        href="/services"
        aria-current={active ? "page" : undefined}
        aria-haspopup="true"
        className={cn(
          "inline-flex items-center gap-1 whitespace-nowrap rounded-full px-3 py-2 text-[15px] transition-colors",
          active ? "bg-mint font-medium text-pine" : "text-stone hover:bg-mint hover:text-pine",
        )}
      >
        Dịch vụ
        <ChevronDown aria-hidden className="size-4 transition-transform group-hover:rotate-180 group-focus-within:rotate-180" />
      </Link>

      {/* pt-3 làm "cầu nối" để chuột đi từ chữ xuống menu không bị mất hover */}
      <div className="invisible absolute left-1/2 top-full z-30 -translate-x-1/2 pt-3 opacity-0 transition-all duration-200 group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100">
        <div className="w-[34rem] overflow-hidden rounded-3xl border border-[#f1e6dc] bg-white shadow-[0_20px_50px_-20px_rgba(160,80,20,0.45)]">
          <div className="flex items-center gap-2 bg-[linear-gradient(100deg,#fff6ee,#ffe6cf)] px-5 py-3">
            <PawPrint aria-hidden className="size-4 text-[#f57c1f]" />
            <p className="text-sm font-bold text-[#1f4a3c]">Dịch vụ thú y</p>
          </div>
          {items.length === 0 ? (
            <p className="px-5 py-4 text-sm text-stone">Đang tải danh sách dịch vụ…</p>
          ) : (
            <ul className="grid grid-cols-2 gap-x-2 p-3">
              {items.map((it) => (
                <li key={it.href}>
                  <Link
                    href={it.href}
                    className="flex items-center gap-2 rounded-xl px-3 py-2 text-[14px] text-[#3b2a20] transition-colors hover:bg-[#fff3e8] hover:text-[#f57c1f]"
                  >
                    <span aria-hidden className="size-1.5 shrink-0 rounded-full bg-[#f9a55c]" />
                    {it.label}
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <Link
            href="/services"
            className="flex items-center justify-center gap-1.5 border-t border-[#f1e6dc] py-3 text-sm font-semibold text-[#f57c1f] hover:bg-[#fff6ee]"
          >
            Xem tất cả dịch vụ <ArrowRight aria-hidden className="size-4" />
          </Link>
        </div>
      </div>
    </li>
  );
}

function MobileDrawer({
  links,
  services,
  email,
  isCustomer,
  onClose,
  onSignOut,
}: {
  links: Array<{ href: string; label: string }>;
  services: Array<{ href: string; label: string }>;
  email: string | null;
  isCustomer: boolean;
  onClose: () => void;
  onSignOut: () => void;
}) {
  const [servicesOpen, setServicesOpen] = useState(false);
  return (
    <div className="fixed inset-0 z-50 xl:hidden">
      <button
        aria-label="Đóng menu"
        onClick={onClose}
        className="absolute inset-0 bg-pine/40 backdrop-blur-[2px]"
      />
      <div className="absolute inset-y-0 right-0 flex w-[min(20rem,86vw)] flex-col bg-white">
        <div className="flex h-18 shrink-0 items-center justify-between border-b border-mist px-5">
          <BrandLockup />
          <button
            onClick={onClose}
            aria-label="Đóng menu"
            className="grid size-11 place-items-center rounded-full text-pine hover:bg-mint"
          >
            <X aria-hidden className="size-5" />
          </button>
        </div>

        <nav aria-label="Điều hướng chính" className="min-h-0 flex-1 overflow-y-auto py-3">
          <ul>
            {links.map((l) => (
              <li key={l.href}>
                <div className="flex items-center">
                  <Link
                    href={l.href}
                    onClick={onClose}
                    className="flex h-14 flex-1 items-center px-5 text-[17px] text-pine hover:bg-mint"
                  >
                    {l.label}
                  </Link>
                  {l.href === "/services" && services.length > 0 && (
                    <button
                      onClick={() => setServicesOpen((v) => !v)}
                      aria-expanded={servicesOpen}
                      aria-label="Mở danh sách dịch vụ"
                      className="grid size-14 place-items-center text-pine hover:bg-mint"
                    >
                      <ChevronDown aria-hidden className={cn("size-5 transition-transform", servicesOpen && "rotate-180")} />
                    </button>
                  )}
                </div>
                {l.href === "/services" && servicesOpen && (
                  <ul className="bg-[#fff8f1] py-1">
                    {services.map((sv) => (
                      <li key={sv.href}>
                        <Link
                          href={sv.href}
                          onClick={onClose}
                          className="flex h-11 items-center gap-2 pl-9 pr-5 text-[15px] text-[#3b2a20] hover:text-[#f57c1f]"
                        >
                          <span aria-hidden className="size-1.5 rounded-full bg-[#f9a55c]" />
                          {sv.label}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        </nav>

        <div className="shrink-0 border-t border-mist p-5">
          <ButtonLink href="/appointments/create" onClick={onClose} className="w-full">
            Đặt lịch khám
          </ButtonLink>

          {email ? (
            <>
              <p className="mt-4 truncate text-center text-sm text-stone">{email}</p>
              {!isCustomer && (
                <p className="mt-1 text-center text-sm text-stone">
                  Tài khoản nội bộ — phần mua hàng dành cho tài khoản khách.
                </p>
              )}
              <button
                onClick={onSignOut}
                className="mt-2 h-12 w-full rounded-full text-[15px] text-pine hover:bg-mint"
              >
                Đăng xuất
              </button>
            </>
          ) : (
            <Link
              href="/login"
              onClick={onClose}
              className="mt-3 flex h-12 w-full items-center justify-center rounded-full border border-mist text-[15px] text-pine hover:bg-mint"
            >
              Đăng nhập
            </Link>
          )}
        </div>
      </div>
    </div>
  );
}

/** Tìm sản phẩm nhanh từ header — chuyển sang /products?q=… */
function HeaderSearch() {
  const router = useRouter();
  const [q, setQ] = useState("");
  return (
    <form
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        router.push(q.trim() ? `/products?q=${encodeURIComponent(q.trim())}` : "/products");
      }}
      className="relative mr-1 hidden lg:block"
    >
      <label htmlFor="header-search" className="sr-only">
        Tìm kiếm sản phẩm
      </label>
      <input
        id="header-search"
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Tìm kiếm sản phẩm…"
        className="h-11 w-60 rounded-full border border-mist bg-[#fffcf8] pl-4 pr-11 text-sm outline-none transition focus:border-[#f57c1f] focus:ring-2 focus:ring-[#f57c1f]/15 xl:w-72"
      />
      <button type="submit" aria-label="Tìm" className="absolute right-1 top-1/2 grid size-9 -translate-y-1/2 place-items-center rounded-full text-stone hover:text-[#f57c1f]">
        <Search aria-hidden className="size-4" />
      </button>
    </form>
  );
}
