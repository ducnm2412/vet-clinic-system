"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { CustomerOnly } from "@/components/site/CustomerOnly";
import { CareBanner } from "@/components/site/CareBanner";
import { UserSidebar } from "./UserSidebar";

/**
 * Khung khu vực khách hàng: sidebar trái (giữ nguyên khi chuyển trang) + nội dung phải.
 * Bảo vệ bằng CustomerOnly (RouteGuard hiện có) — chưa đăng nhập thì chuyển về /login.
 */
export function UserLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return (
    <CustomerOnly>
      <div className="bg-[#fffcf8]">
        <div className="mx-auto grid w-full max-w-[1440px] gap-5 px-4 py-6 sm:px-6 lg:grid-cols-[280px_minmax(0,1fr)] lg:gap-8 lg:py-10">
          <UserSidebar />
          {/* key theo đường dẫn để mỗi lần chuyển trang có hiệu ứng hiện nhẹ */}
          <div key={pathname} className="min-w-0 animate-[pageIn_.3s_ease-out]">
            {children}
          </div>
        </div>
      </div>
      <CareBanner
        title={
          <>
            Quản lý dễ dàng – Chăm sóc
            <br />
            toàn diện cho thú cưng!
          </>
        }
        subtitle="Vet Clinic luôn đồng hành cùng bạn và bé yêu"
        cta={{ href: "/appointments/create", label: "Đặt lịch khám ngay" }}
        image={{ src: "/img/cho-meo-ngu.png", alt: "Chú chó con và mèo con ngủ cạnh nhau", width: 672, height: 230 }}
      />
    </CustomerOnly>
  );
}
