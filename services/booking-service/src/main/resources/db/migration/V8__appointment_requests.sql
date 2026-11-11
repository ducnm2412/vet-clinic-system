-- Đặt lịch theo yêu cầu: khách chỉ nói giờ mong muốn, admin xếp bác sĩ sau.
--
-- Lịch kiểu này chưa có khung giờ (slot) của bác sĩ nào, nên slot_id phải để trống được. Lịch cũ
-- đặt qua slot giữ nguyên, không đụng tới. Partial unique index ở V2 vẫn đúng: nhiều hàng
-- slot_id NULL không vi phạm unique.
ALTER TABLE appointments ALTER COLUMN slot_id DROP NOT NULL;

-- Giờ khách mong muốn (lúc gửi yêu cầu).
ALTER TABLE appointments ADD COLUMN preferred_date DATE;
ALTER TABLE appointments ADD COLUMN preferred_time TIME;

-- Giờ admin đề xuất khi không có bác sĩ rảnh đúng giờ khách chọn; chờ khách đồng ý.
ALTER TABLE appointments ADD COLUMN proposed_date DATE;
ALTER TABLE appointments ADD COLUMN proposed_time TIME;

-- Hàng không có slot thì bắt buộc có giờ mong muốn — chặn hàng "không biết khám lúc nào".
ALTER TABLE appointments ADD CONSTRAINT appointments_slot_or_preferred
    CHECK (slot_id IS NOT NULL OR (preferred_date IS NOT NULL AND preferred_time IS NOT NULL));

-- Đếm số yêu cầu đang chiếm một khung giờ (kiểm tra quá tải) và liệt kê lịch chờ xếp cho admin.
CREATE INDEX idx_appointments_preferred ON appointments (preferred_date, preferred_time)
    WHERE slot_id IS NULL;
