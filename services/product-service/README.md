# Product / Inventory Service

Danh mục sản phẩm, tồn kho.

- Database riêng: `product_db` (PostgreSQL)
- Sự kiện lắng nghe: "Đơn hàng thành công" → trừ tồn kho
- Cấu trúc dự kiến: `src/main/java/...`, `Dockerfile`, `pom.xml`
