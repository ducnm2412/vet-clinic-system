-- Email của khách lúc đặt hàng (lấy từ token), để notification-service gửi thư báo trạng thái đơn.
-- Đơn cũ và hoá đơn quầy để trống: khách vẫn nhận thông báo trên chuông, chỉ không có thư.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_email VARCHAR(255);
