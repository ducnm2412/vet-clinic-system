-- Danh muc dich vu day du (chuyen tu frontend/web/src/lib/api/mock-data.ts).
-- Upsert theo slug: dich vu cu (kham-tong-quat, tiem-phong, phau-thuat) duoc cap nhat ten/gia,
-- id giu nguyen nen lich hen cu van tro dung.

INSERT INTO clinic_services (slug, name, description, duration_minutes, reference_price, active, display_order) VALUES
    ('cap-cuu-24-7', 'Cấp cứu thú cưng 24/7', 'Bác sĩ trực 24/7, kể cả lễ Tết, xử lý kịp thời các tình huống nguy cấp.', 30, 300000, true, 1),
    ('kham-tong-quat', 'Khám sức khoẻ định kỳ', 'Kiểm tra sức khoẻ toàn diện, phát hiện sớm bệnh cho chó mèo.', 30, 150000, true, 2),
    ('kham-dieu-tri', 'Khám, tư vấn & điều trị bệnh', 'Bác sĩ chẩn đoán, tư vấn và lên phác đồ điều trị phù hợp.', 30, 150000, true, 3),
    ('tiem-phong', 'Tiêm phòng – tẩy giun', 'Vắc-xin chất lượng, quy trình chích ngừa an toàn, tẩy giun định kỳ.', 20, 200000, true, 4),
    ('xet-nghiem', 'Xét nghiệm', 'Thiết bị hiện đại, xét nghiệm máu, nước tiểu nhanh tại chỗ.', 45, 350000, true, 5),
    ('sieu-am', 'Siêu âm', 'Siêu âm ổ bụng, thai kỳ, chẩn đoán hình ảnh chính xác.', 30, 250000, true, 6),
    ('phau-thuat', 'Phẫu thuật thú cưng', 'Triệt sản và các ca phẫu thuật trong phòng mổ vô trùng.', 120, 1500000, true, 7),
    ('rang-mieng', 'Chăm sóc răng miệng', 'Cạo vôi, vệ sinh răng, điều trị viêm nướu cho thú cưng.', 45, 250000, true, 8),
    ('spa-grooming', 'Tắm sấy, spa & cắt tỉa', 'Pet grooming: tắm, sấy, cắt tỉa tạo kiểu, vệ sinh tai móng.', 90, 300000, true, 9)
ON CONFLICT (slug) DO UPDATE SET
    name             = EXCLUDED.name,
    description      = EXCLUDED.description,
    duration_minutes = EXCLUDED.duration_minutes,
    reference_price  = EXCLUDED.reference_price,
    active           = EXCLUDED.active,
    display_order    = EXCLUDED.display_order,
    updated_at       = now();

-- "Cham soc va lam dep" da duoc thay bang "spa-grooming": an di, khong xoa (lich hen cu van tro toi).
UPDATE clinic_services SET active = false, display_order = 99, updated_at = now() WHERE slug = 'cham-soc-lam-dep';
