-- V1 đặt DEFAULT 'PENDING', nhưng PaymentStatus đã đổi tên thành PENDING_AMOUNT/PENDING_PAYMENT/
-- COMPLETED (Phase 4) -> 'PENDING' không còn khớp giá trị enum nào. Ứng dụng luôn insert status
-- tường minh nên default này chưa từng được dùng thật, nhưng để lại giá trị sai sẽ gây nhầm lẫn.
ALTER TABLE payments ALTER COLUMN status SET DEFAULT 'PENDING_AMOUNT';
