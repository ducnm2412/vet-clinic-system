-- CN-39: ca trực đã xếp. Một người một ngày có thể nhiều ca (sáng, chiều).
-- user_id là tài khoản bên auth-service — không có khoá ngoại vì khác database.
CREATE TABLE shifts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    date       DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time   TIME NOT NULL,
    note       VARCHAR(500),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Xếp trùng ca cho cùng một người là lỗi nhập liệu, chặn ngay ở database.
    UNIQUE (user_id, date, start_time),
    CONSTRAINT shift_time_order CHECK (end_time > start_time)
);

CREATE INDEX idx_shifts_date ON shifts (date, user_id);

-- CN-38: chấm công thực tế. Mỗi người mỗi ngày một bản ghi: vào ca rồi ra ca.
CREATE TABLE attendance_records (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    date         DATE NOT NULL,
    check_in_at  TIMESTAMPTZ NOT NULL,
    check_out_at TIMESTAMPTZ,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, date),
    CONSTRAINT attendance_time_order CHECK (check_out_at IS NULL OR check_out_at >= check_in_at)
);

CREATE INDEX idx_attendance_date ON attendance_records (date, user_id);
