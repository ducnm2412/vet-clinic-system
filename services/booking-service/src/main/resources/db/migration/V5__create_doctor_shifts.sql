-- CN-39 / VD-11: bản sao ca trực của bác sĩ, nhận từ sự kiện staff.events của staff-service.
-- booking-service giữ bản riêng để sinh khung giờ khám mà không phải hỏi sang service khác mỗi
-- lần chạy (lượt sinh slot chạy nền, không có token của người dùng nào để gọi kèm).
CREATE TABLE doctor_shifts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_user_id UUID NOT NULL,
    date           DATE NOT NULL,
    start_time     TIME NOT NULL,
    end_time       TIME NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (doctor_user_id, date, start_time)
);

CREATE INDEX idx_doctor_shifts_lookup ON doctor_shifts (doctor_user_id, date);
