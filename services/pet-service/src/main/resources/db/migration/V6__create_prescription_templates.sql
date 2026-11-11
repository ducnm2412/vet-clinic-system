-- Đơn mẫu theo bệnh thường gặp, dùng chung cho cả phòng khám. Bác sĩ chọn mẫu để điền sẵn chẩn
-- đoán, hướng điều trị và danh sách thuốc vào bệnh án, rồi chỉnh theo từng con vật.
--
-- Mẫu chỉ là công cụ điền sẵn: bệnh án lưu như cũ (medical_records + prescription_items) và KHÔNG
-- trỏ ngược về mẫu, nên sửa hay xoá mẫu không làm đổi đơn đã kê, và luồng thanh toán không đổi.
CREATE TABLE prescription_templates (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    -- Tên đã bỏ dấu và hạ chữ thường, do service tính (xem V5): chặn hai mẫu trùng tên và tìm không dấu.
    search_name VARCHAR(150) NOT NULL,
    diagnosis   TEXT NOT NULL,
    treatment   TEXT,
    notes       TEXT,
    -- Tài khoản bác sĩ/admin tạo mẫu. Không có khoá ngoại: tài khoản nằm ở auth-service, DB khác.
    created_by  UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_prescription_templates_search_name ON prescription_templates (search_name);

CREATE TABLE prescription_template_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id   UUID NOT NULL REFERENCES prescription_templates(id) ON DELETE CASCADE,
    -- Thuốc trong mẫu bắt buộc nằm trong danh mục. RESTRICT: thuốc đang nằm trong mẫu thì không
    -- xoá cứng được (danh mục chỉ ẩn thuốc, không xoá).
    medication_id UUID NOT NULL REFERENCES medications(id) ON DELETE RESTRICT,
    -- Cùng kiểu và độ dài với prescription_items để chép thẳng sang đơn thật không bị tràn.
    dosage        VARCHAR(100) NOT NULL,
    frequency     VARCHAR(100) NOT NULL,
    duration_days INTEGER,
    notes         TEXT,
    -- Thứ tự thuốc trong mẫu; Postgres không đảm bảo thứ tự dòng khi đọc nếu không ORDER BY.
    sort_order    INTEGER NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_prescription_template_items_template ON prescription_template_items (template_id, sort_order);
CREATE INDEX idx_prescription_template_items_medication ON prescription_template_items (medication_id);
