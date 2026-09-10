import type { Role } from "@/types";
import {
  Boxes,
  CalendarDays,
  ClipboardPlus,
  ClipboardList,
  Contact,
  CreditCard,
  LayoutDashboard,
  PackageSearch,
  PawPrint,
  Receipt,
  Stethoscope,
  UserRound,
  Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Màn hình dựa trên API chưa có — hiện nhãn nhắc để không ai tưởng là hỏng. */
  pending?: boolean;
}

export interface NavSection {
  /** Không đặt tiêu đề cho nhóm đầu; nhóm sau mới cần phân tách. */
  title?: string;
  items: NavItem[];
}

/**
 * Menu dựng theo vai trò. Đây chỉ là chuyện hiển thị — quyền thật do backend quyết,
 * và mọi route còn được chặn thêm một lần ở RouteGuard.
 */
export const NAV: Record<Role, NavSection[]> = {
  ADMIN: [
    { items: [{ href: "/admin", label: "Tổng quan", icon: LayoutDashboard }] },
    {
      title: "Con người",
      items: [
        { href: "/admin/users", label: "Tài khoản", icon: Users, pending: true },
        { href: "/admin/doctors", label: "Bác sĩ", icon: Stethoscope },
        { href: "/admin/staff", label: "Nhân viên", icon: Contact },
        { href: "/admin/customers", label: "Khách hàng", icon: UserRound, pending: true },
      ],
    },
    {
      title: "Khám chữa",
      items: [
        { href: "/admin/appointments", label: "Lịch khám", icon: CalendarDays },
        { href: "/admin/pets", label: "Thú cưng", icon: PawPrint },
      ],
    },
    {
      title: "Kinh doanh",
      items: [
        { href: "/admin/products", label: "Sản phẩm", icon: PackageSearch },
        { href: "/admin/inventory", label: "Tồn kho", icon: Boxes },
        { href: "/admin/orders", label: "Đơn hàng", icon: Receipt },
        { href: "/admin/reports", label: "Báo cáo", icon: ClipboardList, pending: true },
      ],
    },
  ],

  DOCTOR: [
    { items: [{ href: "/doctor", label: "Hôm nay", icon: LayoutDashboard }] },
    {
      items: [
        { href: "/doctor/appointments", label: "Lịch khám", icon: CalendarDays },
        { href: "/doctor/pets", label: "Thú cưng", icon: PawPrint },
        { href: "/doctor/records", label: "Bệnh án", icon: ClipboardPlus },
      ],
    },
    { title: "Cá nhân", items: [{ href: "/doctor/profile", label: "Hồ sơ", icon: UserRound }] },
  ],

  STAFF: [
    { items: [{ href: "/staff", label: "Tổng quan", icon: LayoutDashboard }] },
    {
      title: "Quầy",
      items: [
        { href: "/staff/appointments", label: "Lịch khám", icon: CalendarDays },
        { href: "/staff/payments", label: "Thu tiền thuốc", icon: CreditCard },
        { href: "/staff/orders", label: "Đơn hàng", icon: Receipt },
      ],
    },
    {
      title: "Kho",
      items: [{ href: "/staff/inventory", label: "Tồn kho", icon: Boxes }],
    },
    { title: "Cá nhân", items: [{ href: "/staff/profile", label: "Hồ sơ", icon: UserRound }] },
  ],

  /*
    Khách hàng không dùng dashboard nữa: từ khi có website ở nhóm route (site), họ đi bằng
    đầu trang thương mại chứ không phải thanh bên quản trị. Để mảng rỗng thay vì bỏ khoá,
    vì kiểu Record<Role, …> vẫn đòi đủ bốn vai trò.
  */
  CUSTOMER: [],
};

/** Nơi đưa người dùng tới ngay sau khi đăng nhập, theo vai trò cao nhất. */
export function homeFor(roles: Role[]): string {
  if (roles.includes("ADMIN")) return "/admin";
  if (roles.includes("DOCTOR")) return "/doctor";
  if (roles.includes("STAFF")) return "/staff";
  // Khách hàng về trang chủ của website, không phải một dashboard.
  return "/";
}

/**
 * Vai trò nào được vào nhánh đường dẫn nào.
 *
 * Các trang của khách (/cart, /orders, /pets, /appointments) tự bọc <CustomerOnly>, còn
 * "/" và "/products" mở cho tất cả kể cả người chưa đăng nhập — nên chúng không có mặt
 * ở đây. Bảng này chỉ còn ba nhánh dashboard.
 */
export const ROLE_PREFIX: Record<string, Role[]> = {
  "/admin": ["ADMIN"],
  "/doctor": ["DOCTOR"],
  "/staff": ["STAFF", "ADMIN"],
};
