import type { NextConfig } from "next";

// Trình duyệt gọi thẳng gateway sẽ dính CORS, nên mọi request đi qua /api/* của chính
// Next rồi được rewrite sang API Gateway — cùng origin với trang, không cần cấu hình CORS
// ở backend. Trong Docker biến này là http://api-gateway:8080 (tên service, không phải
// localhost, vì các container gọi nhau qua mạng nội bộ của Docker).
const gateway = process.env.API_GATEWAY_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Gói sẵn server tối giản kèm đúng dependency cần thiết, để image Docker không phải
  // mang theo toàn bộ node_modules.
  output: "standalone",

  async rewrites() {
    return [{ source: "/api/:path*", destination: `${gateway}/:path*` }];
  },

  /*
    Khách hàng từng dùng dashboard ở /customer/*; giờ họ có website riêng ở gốc.
    Giữ lại đường chuyển hướng để link cũ ai đó đã lưu hoặc gửi cho nhau không chết.
    Dùng 307 (tạm thời) chứ không phải 308: nếu sau này cấu trúc còn đổi nữa thì trình
    duyệt không nhớ cứng đường cũ.
  */
  async redirects() {
    const moved: Array<[string, string]> = [
      ["/customer/shop", "/products"],
      ["/customer/cart", "/cart"],
      ["/customer/orders/:id", "/orders/:id"],
      ["/customer/orders", "/orders"],
      ["/customer/pets", "/pets"],
      ["/customer/appointments/create", "/appointments/create"],
      ["/customer/appointments", "/appointments"],
      ["/customer", "/"],
    ];

    return moved.map(([source, destination]) => ({ source, destination, permanent: false }));
  },
};

export default nextConfig;
