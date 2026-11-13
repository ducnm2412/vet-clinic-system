-- Thông báo trong app (chuông ở đầu trang). Mỗi dòng là một thông báo gửi cho một người dùng.
-- user_id trỏ tới auth-service (khác DB nên không có FK thật).
CREATE TABLE notifications (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    -- Đường dẫn tương đối trong frontend, vd /appointments/{id}; null nếu không có trang đích.
    link       VARCHAR(300),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Danh sách mới nhất của một người, và đếm số chưa đọc.
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
