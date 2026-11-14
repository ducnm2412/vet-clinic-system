-- Tiền dịch vụ (khám, tiêm, siêu âm...) của lịch hẹn, tính cạnh tiền thuốc: amount = service_amount + tổng
-- các dòng thuốc. NULL = lịch không chọn dịch vụ hoặc phiếu tạo trước khi có cột này; quầy nhập tay.
ALTER TABLE payments ADD COLUMN service_name   VARCHAR(120);
ALTER TABLE payments ADD COLUMN service_amount NUMERIC(12, 2);
ALTER TABLE payments ADD CONSTRAINT chk_payments_service_amount_nonneg CHECK (service_amount IS NULL OR service_amount >= 0);
