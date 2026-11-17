-- Ngày nghỉ do admin ghi tay khi bác sĩ/nhân viên báo nghỉ (không có đơn xin nghỉ).
-- Có ngày nghỉ thì ngày đó không xếp ca được, và ca đã xếp bị gỡ lúc ghi nghỉ.
-- user_id là tài khoản bên auth-service — không có khoá ngoại vì khác database.
CREATE TABLE leaves (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    date       DATE NOT NULL,
    reason     VARCHAR(500),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Một người một ngày chỉ nghỉ một lần: ghi lại là chuyện thường nên chặn trùng ở database.
    UNIQUE (user_id, date)
);

CREATE INDEX idx_leaves_date ON leaves (date, user_id);
