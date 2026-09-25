-- Bệnh án và đơn thuốc, chuyển từ booking-service sang (VD-10 chặng 2).
--
-- Trước đây bảng này nằm trong booking_db và trỏ thẳng vào appointments bằng khoá ngoại. Ở đây
-- appointment_id chỉ là UUID thường: lịch hẹn thuộc booking-service, khác database nên không có
-- khoá ngoại thật. Đổi lại, pet_id CÓ khoá ngoại vì thú cưng nằm ngay trong service này.
--
-- customer_user_id và doctor_user_id được chép vào lúc lập bệnh án (pet-service hỏi
-- booking-service một lần) chứ không hỏi lại mỗi lần đọc: bệnh án là dữ liệu lâm sàng, phải đọc
-- được cả khi booking-service đang tắt, và ai khám thì vĩnh viễn là người đó dù lịch hẹn sau này
-- có bị sửa.
CREATE TABLE medical_records (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id   UUID NOT NULL UNIQUE,
    -- ON DELETE RESTRICT: đã có bệnh án thì không cho xoá hồ sơ thú cưng. Bệnh án là hồ sơ lâm
    -- sàng của phòng khám, không phải dữ liệu riêng của khách muốn xoá là xoá.
    pet_id           UUID NOT NULL REFERENCES pets (id) ON DELETE RESTRICT,
    customer_user_id UUID NOT NULL,
    doctor_user_id   UUID NOT NULL,
    diagnosis        TEXT NOT NULL,
    treatment        TEXT,
    notes            TEXT,
    -- PENDING khi bác sĩ vừa kê (chờ thanh toán) -> PAID khi khách trả tiền -> RECEIVED khi đã
    -- giao thuốc.
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Lịch sử khám của một con vật (CN-24) — truy vấn thường xuyên nhất sau khi tách service.
CREATE INDEX idx_medical_records_pet ON medical_records (pet_id, created_at DESC);
-- "Bệnh án còn treo" của một bác sĩ, và hàng đợi đơn thuốc đã trả tiền của quầy.
CREATE INDEX idx_medical_records_doctor_status ON medical_records (doctor_user_id, status);
CREATE INDEX idx_medical_records_status ON medical_records (status);

CREATE TABLE prescription_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medical_record_id UUID NOT NULL REFERENCES medical_records (id) ON DELETE CASCADE,
    medication_name   VARCHAR(255) NOT NULL,
    dosage            VARCHAR(100) NOT NULL,
    frequency         VARCHAR(100) NOT NULL,
    duration_days     INTEGER,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
