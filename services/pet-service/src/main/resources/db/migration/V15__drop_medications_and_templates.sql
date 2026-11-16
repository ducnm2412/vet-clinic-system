-- Danh mục thuốc và đơn mẫu đã chuyển sang product-service (product_db, V3-V5), nơi sẽ quản lý
-- kho thuốc. Dữ liệu đã được chép sang đó nguyên UUID trước khi bảng ở đây bị xoá.
--
-- prescription_items KHÔNG bị đụng tới: đơn thuốc đã kê vẫn lưu tên thuốc dạng chữ trong bệnh án.
DROP TABLE IF EXISTS prescription_template_items;
DROP TABLE IF EXISTS prescription_templates;
DROP TABLE IF EXISTS medications;
