-- Danh mục thuốc dùng chung của phòng khám: để bác sĩ gõ vài chữ là gợi ý ra tên thuốc, và để
-- mỗi thuốc có sẵn một dòng lưu ý (chống chỉ định, cách dùng) hiện ra khi kê.
--
-- Đây chỉ là danh mục tra cứu. Đơn thuốc thật (prescription_items) vẫn lưu tên thuốc dạng chữ,
-- KHÔNG trỏ khoá ngoại vào đây: sửa tên hay ẩn một thuốc khỏi danh mục không được làm đổi hồ sơ
-- bệnh án đã kê.
CREATE TABLE medications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    -- Tên đã bỏ dấu và hạ chữ thường, do service tính. Tìm "smecta" hay "tiêu độc" đều ra mà
    -- không cần extension unaccent của Postgres (cần quyền tạo extension trên DB).
    search_name VARCHAR(255) NOT NULL,
    unit        VARCHAR(50),
    caution     TEXT,
    -- Ẩn thay vì xoá: các đơn mẫu (V6) sẽ trỏ vào thuốc, xoá cứng sẽ bị khoá ngoại chặn.
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Không cho hai thuốc trùng tên kể cả khi khác hoa/thường hay dấu ("Smecta" / "smecta").
CREATE UNIQUE INDEX uq_medications_search_name ON medications (search_name);
