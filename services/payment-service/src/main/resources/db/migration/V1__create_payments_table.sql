CREATE TABLE payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 1 bệnh án (đơn thuốc) chỉ có tối đa 1 payment — khớp với PrescriptionCreatedEvent (booking-service).
    medical_record_id UUID NOT NULL UNIQUE,
    appointment_id    UUID NOT NULL,
    customer_user_id  UUID NOT NULL,
    -- Chưa có price catalog ở booking-service -> amount do staff nhập tay khi tạo/duyệt payment.
    amount            NUMERIC(12, 2) NOT NULL,
    -- NULL cho tới khi khách hàng/staff chọn phương thức lúc thanh toán.
    method            VARCHAR(20),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id        UUID NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    -- Snapshot từ PrescriptionCreatedEvent.Item tại thời điểm tạo payment.
    medication_name   VARCHAR(255) NOT NULL,
    dosage            VARCHAR(100) NOT NULL,
    frequency         VARCHAR(100) NOT NULL,
    duration_days     INTEGER,
    unit_price        NUMERIC(12, 2),
    quantity          INTEGER,
    line_amount       NUMERIC(12, 2),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_appointment_id ON payments (appointment_id);
CREATE INDEX idx_payments_customer_user_id ON payments (customer_user_id);
