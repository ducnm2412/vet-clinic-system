-- Thời điểm cấp link thanh toán hiện tại. VNPAY đòi đúng thời điểm này (vnp_CreateDate của link) khi hỏi
-- lại trạng thái giao dịch, nên phải lưu lại thay vì suy ra. Idempotent như các migration trước.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS gateway_txn_created_at TIMESTAMPTZ;
