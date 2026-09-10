import type { Metadata } from "next";
import { Be_Vietnam_Pro, Bitter, Fraunces } from "next/font/google";
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

/*
  Chữ tiêu đề của website khách hàng. Fraunces là serif mềm, có trục SOFT và WONK làm
  chân chữ hơi lệch một cách có duyên — ấm và thân thiện, không nghiêm nghị kiểu bệnh
  viện, cũng không phải Playfair mà trang nào cũng dùng. Xin cả hai trục đó ở đây vì
  next/font chỉ tải trục `wght` nếu không kê tên trục ra.
*/
const brand = Fraunces({
  variable: "--font-brand",
  subsets: ["latin", "vietnamese"],
  axes: ["SOFT", "WONK", "opsz"],
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Vet Clinic — Phòng khám thú y",
    template: "%s · Vet Clinic",
  },
  description:
    "Phòng khám thú y với đội ngũ bác sĩ giàu kinh nghiệm. Đặt lịch khám, mua đồ cho thú cưng và theo dõi hồ sơ sức khoẻ của bé nhà bạn.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="vi"
      className={`${sans.variable} ${display.variable} ${brand.variable} h-full antialiased`}
    >
      <body className="min-h-full">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
