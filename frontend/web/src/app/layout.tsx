import type { Metadata } from "next";
import { Be_Vietnam_Pro, Bitter } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

// Giao diện, bảng, biểu mẫu — dấu tiếng Việt đầy đủ, đọc tốt ở cỡ nhỏ.
const sans = Be_Vietnam_Pro({
  variable: "--font-sans",
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600"],
  display: "swap",
});

// Slab serif cho tiêu đề: gợi tài liệu lâm sàng, chắc chắn, không thanh mảnh kiểu tạp chí.
const display = Bitter({
  variable: "--font-display",
  subsets: ["latin", "vietnamese"],
  weight: ["500", "600"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "Vet Clinic — Quản lý phòng khám thú y",
  description: "Lịch khám, bệnh án, kho thuốc và đơn hàng trong một hệ thống",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi" className={`${sans.variable} ${display.variable} h-full antialiased`}>
      <body className="min-h-full">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
