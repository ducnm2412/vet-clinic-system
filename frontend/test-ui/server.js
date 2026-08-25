// Server tối giản (không phụ thuộc npm package nào) để:
// 1. Serve file index.html tĩnh.
// 2. Proxy toàn bộ /api-proxy/* sang api-gateway — tránh lỗi CORS vì các service
//    chưa cấu hình CORS cho browser.
//
// Chạy: node server.js  (mặc định http://localhost:3000)

const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = process.env.PORT || 3000;
const API_GATEWAY_URL = process.env.API_GATEWAY_URL || "http://localhost:8080";

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
};

async function proxy(req, res, upstreamBase) {
  const target = upstreamBase + req.url.replace(/^\/api-proxy/, "");

  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const body = Buffer.concat(chunks);

  try {
    const upstream = await fetch(target, {
      method: req.method,
      headers: {
        "Content-Type": "application/json",
        ...(req.headers.authorization ? { authorization: req.headers.authorization } : {}),
      },
      body: body.length > 0 ? body : undefined,
    });

    const text = await upstream.text();
    res.writeHead(upstream.status, {
      "Content-Type": upstream.headers.get("content-type") || "application/json",
    });
    res.end(text);
  } catch (err) {
    res.writeHead(502, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        message: `Không kết nối được tới ${upstreamBase}. Kiểm tra service tương ứng đã chạy chưa. Chi tiết: ${err.message}`,
      })
    );
  }
}

function serveStatic(req, res) {
  const filePath = path.join(__dirname, req.url === "/" ? "/index.html" : req.url);
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Không tìm thấy trang.");
      return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, { "Content-Type": MIME_TYPES[ext] || "application/octet-stream" });
    res.end(data);
  });
}

const server = http.createServer((req, res) => {
  if (req.url.startsWith("/api-proxy/")) {
    proxy(req, res, API_GATEWAY_URL);
    return;
  }
  serveStatic(req, res);
});

server.listen(PORT, () => {
  console.log(`Test UI đang chạy tại http://localhost:${PORT}`);
  console.log(`Proxy /auth, /admin, /profile sang api-gateway: ${API_GATEWAY_URL}`);
});
