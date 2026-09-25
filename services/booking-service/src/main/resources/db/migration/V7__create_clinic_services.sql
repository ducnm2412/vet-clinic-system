-- VD-21: danh mục dịch vụ của phòng khám.
--
-- Trước đây bốn dịch vụ chỉ là nội dung tĩnh trong frontend, và khách muốn nói "tôi cần tiêm
-- phòng" thì phải gõ vào ô lý do khám — không tra cứu, không thống kê được.
--
-- Tên bảng là clinic_services chứ không phải services: "service" trong dự án này còn nghĩa là
-- microservice, đọc code sẽ lẫn.
CREATE TABLE clinic_services (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- slug để frontend chọn biểu tượng và giữ liên kết cũ; đổi tên hiển thị không làm hỏng.
    slug             VARCHAR(80) NOT NULL UNIQUE,
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    -- Thời lượng để nhân viên ước lượng và hiện cho khách. CHƯA dùng để chia khung giờ: mọi ca
    -- vẫn 30 phút như cũ, đổi cách chia slot là việc riêng, xem VD-21.
    duration_minutes INTEGER NOT NULL DEFAULT 30,
    -- Giá tham khảo, có thể để trống: nhiều ca phải khám xong mới báo giá được.
    reference_price  NUMERIC(12, 2),
    -- Ngừng cung cấp thì ẩn, không xoá — lịch hẹn cũ vẫn trỏ tới (cùng lý do với VD-03).
    active           BOOLEAN NOT NULL DEFAULT true,
    display_order    INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bốn dịch vụ đang hiện trên trang chủ, chuyển nguyên văn từ frontend/web/src/config/clinic.ts.
INSERT INTO clinic_services (slug, name, description, duration_minutes, display_order) VALUES
    ('kham-tong-quat', 'Khám tổng quát',
     'Bác sĩ nghe tim phổi, kiểm tra răng miệng, da lông và cân nặng, rồi ghi lại thành bệnh án để lần sau còn đối chiếu.',
     30, 1),
    ('tiem-phong', 'Tiêm phòng',
     'Vắc xin dại, care, parvo và các mũi nhắc theo lịch. Chúng tôi giữ lịch giúp bạn và nhắc trước ngày đến hạn.',
     20, 2),
    ('phau-thuat', 'Phẫu thuật',
     'Triệt sản, lấy dị vật, xử lý vết thương. Có phòng mổ riêng, gây mê theo cân nặng và theo dõi đến khi bé tỉnh hẳn.',
     90, 3),
    ('cham-soc-lam-dep', 'Chăm sóc và làm đẹp',
     'Tắm, cắt tỉa, vệ sinh tai và cắt móng. Bé nào sợ nước thì làm chậm, không ép, không nhốt chờ cả buổi.',
     60, 4);

-- Lịch hẹn cũ không có dịch vụ nên để trống được. Không CASCADE: dịch vụ chỉ ẩn chứ không xoá.
ALTER TABLE appointments ADD COLUMN service_id UUID REFERENCES clinic_services (id);
