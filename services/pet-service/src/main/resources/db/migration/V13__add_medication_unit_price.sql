-- Giá bán một đơn vị thuốc (VND), để phiếu thu tiền thuốc tự điền đơn giá. NULL = chưa có giá:
-- quầy nhập tay trên phiếu thu. Đổi giá ở đây không làm đổi phiếu thu đã tạo (phiếu giữ giá lúc kê).
ALTER TABLE medications ADD COLUMN unit_price NUMERIC(12, 2);
ALTER TABLE medications ADD CONSTRAINT chk_medications_unit_price_nonneg CHECK (unit_price IS NULL OR unit_price >= 0);
