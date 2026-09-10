"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LogOut, Menu, ShoppingBag, User, X } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { useCart } from "@/lib/useCart";
import { cn } from "@/lib/utils/cn";
import { BrandLockup } from "./Brand";
import { ButtonLink } from "./primitives";

/** Mục nào cần đăng nhập bằng tài khoản khách thì đánh dấu, để không mời người ta vào chỗ 401. */
const LINKS: Array<{ href: string; label: string; customerOnly?: boolean }> = [
  { href: "/", label: "Trang chủ" },
  { href: "/#dich-vu", label: "Dịch vụ" },
  { href: "/products", label: "Sản phẩm" },
  { href: "/pets", label: "Thú cưng", customerOnly: true },
  { href: "/appointments", label: "Lịch khám", customerOnly: true },
  { href: "/orders", label: "Đơn hàng", customerOnly: true },
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
  const links = LINKS.filter((l) => !l.customerOnly || isCustomer);

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
        "sticky top-0 z-50 bg-white/95 backdrop-blur transition-shadow duration-300",
        scrolled ? "shadow-[0_1px_0_var(--mist),0_6px_20px_-18px_rgb(19_78_74/0.5)]" : "",
      )}
    >
      <div className="mx-auto flex h-18 w-full max-w-[1200px] items-center gap-6 px-5 md:px-6">
        <BrandLockup />

        <nav aria-label="Điều hướng chính" className="ml-2 hidden xl:block">
          <ul className="flex items-center gap-1">
            {links.map((l) => (
              <li key={l.href}>
                <Link
                  href={l.href}
                  aria-current={isActive(l.href) ? "page" : undefined}
                  className={cn(
                    "rounded-full px-3.5 py-2 text-[15px] transition-colors",
                    isActive(l.href)
                      ? "bg-mint font-medium text-pine"
                      : "text-stone hover:bg-mint hover:text-pine",
                  )}
                >
                  {l.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <div className="ml-auto flex items-center gap-2">
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
                className="grid size-11 place-items-center rounded-full border border-mist text-pine transition-colors hover:border-teal hover:bg-mint"
              >
                <User aria-hidden className="size-5 text-teal" />
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
                    className="absolute right-0 z-20 mt-2 w-64 overflow-hidden rounded-2xl border border-mist bg-white py-1 shadow-[var(--shadow-lift)]"
                  >
                    <p className="truncate px-4 pb-2 pt-3 text-sm text-stone">{email}</p>
                    <hr className="mb-1 border-mist" />
                    {isCustomer && (
                      <>
                        <AccountItem href="/pets" onClick={() => setAccountOpen(false)}>
                          Thú cưng của tôi
                        </AccountItem>
                        <AccountItem href="/appointments" onClick={() => setAccountOpen(false)}>
                          Lịch khám của tôi
                        </AccountItem>
                        <AccountItem href="/orders" onClick={() => setAccountOpen(false)}>
                          Đơn hàng của tôi
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
      className="block px-4 py-2.5 text-[15px] text-pine hover:bg-mint"
    >
      {children}
    </Link>
  );
}

/**
 * Ngăn kéo trên điện thoại: trượt từ phải, mỗi mục cao 56px để ngón tay bấm không trượt.
 * Không phải bản thu nhỏ của thanh điều hướng máy để bàn.
 */
function MobileDrawer({
  links,
  email,
  isCustomer,
  onClose,
  onSignOut,
}: {
  links: Array<{ href: string; label: string }>;
  email: string | null;
  isCustomer: boolean;
  onClose: () => void;
  onSignOut: () => void;
}) {
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
                <Link
                  href={l.href}
                  onClick={onClose}
                  className="flex h-14 items-center px-5 text-[17px] text-pine hover:bg-mint"
                >
                  {l.label}
                </Link>
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
