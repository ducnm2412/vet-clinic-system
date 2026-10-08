-- Hoá đơn gộp tại quầy: một order chứa tiền hàng + tiền khám/thuốc, thu một lần bằng tiền mặt
-- hoặc chuyển khoản. Viết idempotent (IF NOT EXISTS) để chạy lại trên DB đã có cột cũng không hỏng.

-- Khách lẻ mua tại quầy không có tài khoản; hoá đơn tại quầy cũng chỉ có phương thức lúc thu tiền.
ALTER TABLE orders ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE orders ALTER COLUMN payment_method DROP NOT NULL;

-- Kênh bán: ONLINE (giỏ hàng, giao tận nơi) hay COUNTER (nhân viên lập tại quầy).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS channel VARCHAR(20) NOT NULL DEFAULT 'ONLINE';

-- Tiền khám/thuốc tham chiếu sang payment-service (khác database nên không có khoá ngoại).
-- exam_amount chụp lại số tiền lúc lập hoá đơn; nằm trong total nhưng KHÔNG tính vào doanh thu
-- đơn hàng, vì payment-service đã tính khoản khám đó rồi.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS exam_payment_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS exam_amount NUMERIC(14, 2) NOT NULL DEFAULT 0;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;
-- Mã giao dịch ngân hàng do nhân viên đối chiếu và ghi lại khi thu chuyển khoản.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS transfer_reference VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_exam_amount') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_exam_amount CHECK (exam_amount >= 0);
    END IF;
END $$;

-- Đơn COD đã giao xong là đã thu tiền; bù cho các đơn tạo trước khi có cột payment_status.
UPDATE orders
SET payment_status = 'PAID', paid_at = completed_at
WHERE status = 'COMPLETED' AND payment_status = 'UNPAID';

-- Một khoản khám chỉ nằm trong tối đa một hoá đơn, nên không thể thu hai lần.
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_exam_payment
    ON orders (exam_payment_id) WHERE exam_payment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_channel ON orders (channel, created_at DESC);
