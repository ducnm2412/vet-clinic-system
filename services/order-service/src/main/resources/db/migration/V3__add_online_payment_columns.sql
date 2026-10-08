-- Thanh toán online cho đơn đặt qua giỏ hàng. Viết idempotent (IF NOT EXISTS) như V2.

-- Hạn trả tiền của đơn ONLINE chưa thanh toán; quá hạn thì job tự huỷ đơn. NULL với đơn COD/tại quầy.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMPTZ;

-- Mã giao dịch phía cổng thanh toán, ghi lại khi cổng báo kết quả để đối soát.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_txn_ref VARCHAR(64);

-- Job quét đơn quá hạn chỉ cần nhìn đúng nhóm đơn đang chờ trả tiền.
CREATE INDEX IF NOT EXISTS idx_orders_awaiting_payment
    ON orders (payment_expires_at)
    WHERE payment_expires_at IS NOT NULL AND payment_status = 'UNPAID' AND status = 'PENDING';
