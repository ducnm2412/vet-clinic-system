-- Bệnh án (kèm đơn thuốc) sau khi bác sĩ kê xong ở trạng thái PENDING, chờ staff tiếp nhận
-- (RECEIVED) để xử lý/giao thuốc cho khách hàng.
ALTER TABLE medical_records ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
