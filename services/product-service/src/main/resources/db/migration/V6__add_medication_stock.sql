-- Kho thuốc: tồn hiện tại nằm ngay trên medications (giống products), mọi thay đổi đều ghi vết
-- sang medication_stock_movements để đối soát. Tồn đầu của các thuốc đã có là 0, nhân viên nhập dần.
ALTER TABLE medications ADD COLUMN stock_quantity INTEGER NOT NULL DEFAULT 0
    CONSTRAINT chk_medications_stock_nonneg CHECK (stock_quantity >= 0);
ALTER TABLE medications ADD COLUMN low_stock_threshold INTEGER NOT NULL DEFAULT 0
    CONSTRAINT chk_medications_threshold_nonneg CHECK (low_stock_threshold >= 0);

CREATE TABLE medication_stock_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medication_id   UUID NOT NULL REFERENCES medications (id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL,
    -- Âm khi xuất kho, dương khi nhập. quantity_after chốt tồn sau thao tác để đối soát.
    quantity_change INTEGER NOT NULL,
    quantity_after  INTEGER NOT NULL CHECK (quantity_after >= 0),
    -- Phần thuốc đã phát nhưng kho không đủ: khách đã nhận thuốc nên không chặn thu tiền, tồn về 0 và
    -- phần thiếu ghi ở đây để nhân viên biết đường bổ sung / đối chiếu.
    shortfall       INTEGER NOT NULL DEFAULT 0 CHECK (shortfall >= 0),
    note            VARCHAR(500),
    -- Với type=SALE đây là paymentId của phiếu thu: chống trừ hai lần khi RabbitMQ giao lại message.
    reference_id    UUID,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_medication_stock_movements_med ON medication_stock_movements (medication_id, created_at DESC);
CREATE UNIQUE INDEX idx_medication_stock_movements_reference
    ON medication_stock_movements (medication_id, reference_id, type)
    WHERE reference_id IS NOT NULL;
