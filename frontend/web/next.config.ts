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
};

export default nextConfig;
