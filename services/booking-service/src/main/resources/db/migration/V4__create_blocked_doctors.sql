-- CN-08: bác sĩ đang bị khoá tài khoản (nhận từ sự kiện user.locked của auth-service).
-- booking-service giữ bản riêng để không sinh slot mới cho họ mà khỏi gọi sang auth-service.
CREATE TABLE blocked_doctors (
    doctor_user_id UUID PRIMARY KEY,
    blocked_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
