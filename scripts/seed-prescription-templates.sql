-- DỮ LIỆU THỬ: vài đơn mẫu cho phòng khám (pet-service: prescription_templates + items).
--
-- Dựng từ bảng bệnh thường gặp phòng khám cung cấp, dùng thuốc đã có trong scripts/seed-medications.sql
-- (nạp file đó trước). KHÔNG phải phác đồ: liều ghi "theo cân nặng / theo nhãn" chứ không có số mg/kg,
-- bác sĩ cần rà lại trước khi dùng thật.
--
-- created_by là id người dùng bên auth-db (không có khoá ngoại), nên truyền vào lúc chạy:
--   PGPASSWORD=... psql -h 127.0.0.1 -p 5440 -U postgres -d pet_db \
--     -v ON_ERROR_STOP=1 -v created_by=<uuid-bác-sĩ> -f scripts/seed-prescription-templates.sql
-- Chạy lại được: mẫu đã có theo search_name thì bỏ qua (cả các dòng thuốc của nó).

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm da dị ứng - chó', 'viem da di ung - cho', 'Viêm da dị ứng ở chó: ngứa, đỏ da, gãi nhiều.', 'Giảm ngứa và viêm; tìm và hạn chế tác nhân gây dị ứng (thức ăn, bọ chét, môi trường).', 'Tái khám sau 7 ngày. Giữ da khô, sạch.', :'created_by'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', '7', 'Uống sau ăn. Không tự ngừng đột ngột, giảm liều theo chỉ định của bác sĩ.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Ghẻ Demodex - chó', 'ghe demodex - cho', 'Viêm da do ghẻ Demodex ở chó.', 'Diệt ký sinh trùng. Điều trị kéo dài đến khi cạo da soi kính âm tính.', 'Tái khám và cạo da kiểm tra định kỳ.', :'created_by'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('afoxolaner (nexgard)', 'Theo cân nặng', '1 lần/tháng', NULL, 'Hỏi tiền sử động kinh hoặc co giật trước khi dùng.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm tai ngoài - chó', 'viem tai ngoai - cho', 'Viêm tai ngoài ở chó.', 'Vệ sinh ống tai rồi nhỏ thuốc theo phác đồ.', 'Tái khám sau 7-10 ngày. Không dùng thuốc nhỏ tai khi nghi màng nhĩ thủng.', :'created_by'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('epi-otic', 'Vừa đủ làm sạch ống tai', '1 lần/ngày', '7', 'Làm sạch tai trước khi nhỏ thuốc.', 0),
    ('surolan', 'Theo nhãn sản phẩm', '2 lần/ngày', '7', 'Nhỏ sau khi đã vệ sinh tai.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Nấm da - mèo', 'nam da - meo', 'Nấm da (Ringworm) ở mèo.', 'Thuốc uống kết hợp tắm thuốc ngoài da; vệ sinh môi trường.', 'Nấm có thể lây sang người: cách ly mèo bệnh và vệ sinh môi trường. Điều trị đến khi cấy nấm âm tính.', :'created_by'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('itraconazole', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống cùng thức ăn. Theo dõi men gan khi dùng kéo dài.', 0),
    ('lime sulfur', 'Pha theo nhãn sản phẩm', '2 lần/tuần', NULL, 'Dùng ngoài da. Ngăn mèo liếm cho đến khi khô.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;
