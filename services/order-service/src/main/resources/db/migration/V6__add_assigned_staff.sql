-- Nhân viên phụ trách đơn: hệ thống chia cho nhân viên đang có ca khi đơn sẵn sàng để xử lý, nhân viên khác có thể
-- nhận lại. Để trống khi lúc đó không ai có ca (đơn vẫn báo cho mọi nhân viên) và với đơn cũ.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_staff_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_staff_name VARCHAR(200);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_orders_assigned_staff ON orders (assigned_staff_id, assigned_at);
