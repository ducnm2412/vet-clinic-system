-- Don thuoc mau theo benh thuong gap (pet-service: prescription_templates + prescription_template_items).
--
-- Dung tu bang benh phong kham cung cap, dung thuoc trong V7. KHONG phai phac do: lieu ghi "theo can
-- nang / theo nhan / theo chi dinh" chu khong co so mg/kg, bac si can ra lai truoc khi dung that.
--
-- created_by la id nguoi dung o auth-db (khong co khoa ngoai). Don mau nap san la du lieu he thong,
-- khong thuoc tai khoan nao nen dung UUID rong (all-zero).
-- ON CONFLICT DO NOTHING theo search_name: mau da co thi bo qua ca cac dong thuoc cua no. Moi khoi
-- JOIN medications theo search_name nen thuoc nao bi xoa khoi V7 thi dong do khong duoc tao.

-- ---- Don mau co san (truoc day nap bang scripts/seed-prescription-templates.sql) ----
WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm da dị ứng - chó', 'viem da di ung - cho', 'Viêm da dị ứng ở chó: ngứa, đỏ da, gãi nhiều.', 'Giảm ngứa và viêm; tìm và hạn chế tác nhân gây dị ứng (thức ăn, bọ chét, môi trường).', 'Tái khám sau 7 ngày. Giữ da khô, sạch.', '00000000-0000-0000-0000-000000000000'::uuid)
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
  VALUES ('Ghẻ Demodex - chó', 'ghe demodex - cho', 'Viêm da do ghẻ Demodex ở chó.', 'Diệt ký sinh trùng. Điều trị kéo dài đến khi cạo da soi kính âm tính.', 'Tái khám và cạo da kiểm tra định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
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
  VALUES ('Viêm tai ngoài - chó', 'viem tai ngoai - cho', 'Viêm tai ngoài ở chó.', 'Vệ sinh ống tai rồi nhỏ thuốc theo phác đồ.', 'Tái khám sau 7-10 ngày. Không dùng thuốc nhỏ tai khi nghi màng nhĩ thủng.', '00000000-0000-0000-0000-000000000000'::uuid)
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
  VALUES ('Nấm da - mèo', 'nam da - meo', 'Nấm da (Ringworm) ở mèo.', 'Thuốc uống kết hợp tắm thuốc ngoài da; vệ sinh môi trường.', 'Nấm có thể lây sang người: cách ly mèo bệnh và vệ sinh môi trường. Điều trị đến khi cấy nấm âm tính.', '00000000-0000-0000-0000-000000000000'::uuid)
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

-- ---- Cac benh con lai trong bang (cho, meo, chim canh) ----

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Leptospirosis - chó', 'leptospirosis - cho', 'Leptospirosis ở chó: sốt, vàng da, tiểu ít, suy thận.', 'Kháng sinh diệt khuẩn, truyền dịch hỗ trợ thận; cách ly thú bệnh.', 'Bệnh lây sang người: vệ sinh kỹ khi chăm sóc. Tái khám theo dõi chức năng gan thận.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('doxycycline', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Cho uống cùng thức ăn.', 0),
    ('ampicillin', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Không dùng khi thú có tiền sử dị ứng penicillin.', 1),
    ('penicillin g', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm gan truyền nhiễm (CAV-1) - chó', 'viem gan truyen nhiem (cav-1) - cho', 'Viêm gan truyền nhiễm (CAV-1) ở chó: sốt, đau bụng, giác mạc đục xanh.', 'Điều trị hỗ trợ: truyền dịch, kháng sinh chống bội nhiễm, thuốc bổ gan.', 'Bệnh do virus, điều trị chủ yếu là hỗ trợ. Tái khám theo dõi men gan.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('dich truyen', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống sau ăn.', 1),
    ('same (s-adenosylmethionine)', 'Theo nhãn sản phẩm', 'Theo chỉ định của bác sĩ', NULL, NULL, 2),
    ('silymarin', 'Theo nhãn sản phẩm', 'Theo chỉ định của bác sĩ', NULL, NULL, 3)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm tụy - chó', 'viem tuy - cho', 'Viêm tụy ở chó: nôn, đau bụng, bỏ ăn.', 'Chống nôn, giảm đau, truyền dịch; cho nhịn hoặc ăn lại thức ăn ít béo theo chỉ định.', 'Tái khám nếu nôn tái phát hoặc bỏ ăn quá 24 giờ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('maropitant (cerenia)', 'Theo cân nặng', '1 lần/ngày', NULL, 'Kiểm tra độ tuổi tối thiểu theo nhãn.', 0),
    ('buprenorphine', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Có thể gây an thần.', 1),
    ('dich truyen', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Tiểu đường - chó', 'tieu duong - cho', 'Tiểu đường ở chó: uống nhiều, tiểu nhiều, sụt cân.', 'Tiêm insulin đều đặn kết hợp chế độ ăn ổn định.', 'Chủ nuôi cần học cách tiêm và nhận biết hạ đường huyết. Tái khám đo đường huyết định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('insulin (caninsulin)', 'Theo cân nặng', 'Theo lịch tiêm bác sĩ hướng dẫn', NULL, 'Không tự đổi liều.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Suy giáp - chó', 'suy giap - cho', 'Suy giáp ở chó: tăng cân, rụng lông, lờ đờ.', 'Bổ sung hormone tuyến giáp suốt đời.', 'Xét nghiệm T4 sau vài tuần để chỉnh liều, sau đó kiểm tra định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('levothyroxine', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Cho uống đều đặn, cố định giờ trong ngày.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Cushing (cường vỏ thượng thận) - chó', 'cushing (cuong vo thuong than) - cho', 'Cushing ở chó: uống nhiều, bụng phệ, rụng lông đối xứng.', 'Dùng thuốc ức chế sản xuất cortisol, theo dõi bằng test ACTH.', 'Tái khám kiểm tra cortisol theo lịch của bác sĩ; báo ngay nếu thú bỏ ăn, nôn hoặc lờ đờ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('trilostane (vetoryl)', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Cho uống cùng thức ăn.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm khớp, loạn sản xương hông - chó', 'viem khop, loan san xuong hong - cho', 'Viêm khớp, loạn sản xương hông ở chó: đi khập khiễng, khó đứng dậy.', 'Giảm đau, kháng viêm; kiểm soát cân nặng và vận động nhẹ nhàng.', 'Không dùng chung hai thuốc kháng viêm NSAID. Dùng lâu dài cần xét nghiệm gan thận định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('carprofen', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Cho uống sau ăn.', 0),
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, 'Không dùng cùng Carprofen.', 1),
    ('gabapentin', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Có thể gây buồn ngủ.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Dị ứng da (Atopic) - chó', 'di ung da (atopic) - cho', 'Dị ứng da (viêm da cơ địa) ở chó: ngứa, liếm chân, da đỏ.', 'Giảm ngứa và viêm; tìm và hạn chế tác nhân gây dị ứng.', 'Bệnh mạn tính, cần kiểm soát lâu dài. Tái khám sau 2-4 tuần.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('oclacitinib (apoquel)', 'Theo cân nặng', '2 lần/ngày', NULL, 'Không dùng cho chó dưới 12 tháng tuổi.', 0),
    ('lokivetmab (cytopoint)', 'Theo cân nặng', 'Tiêm theo lịch bác sĩ', NULL, NULL, 1),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Không tự ngừng đột ngột.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Nhiễm trùng tiết niệu - chó', 'nhiem trung tiet nieu - cho', 'Nhiễm trùng tiết niệu ở chó: tiểu rắt, tiểu ra máu.', 'Kháng sinh theo kết quả xét nghiệm nước tiểu, cho uống nhiều nước.', 'Tái khám xét nghiệm lại nước tiểu sau khi hết thuốc.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống sau ăn.', 0),
    ('enrofloxacin', 'Theo cân nặng', '1 lần/ngày', NULL, 'Hạn chế dùng cho chó đang lớn.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm tử cung mủ (Pyometra) - chó', 'viem tu cung mu (pyometra) - cho', 'Viêm tử cung mủ ở chó cái: chảy mủ âm hộ, uống nhiều, sốt.', 'Cấp cứu: thường phải phẫu thuật cắt tử cung; kháng sinh và thuốc nội khoa trong trường hợp đặc biệt.', 'Bệnh nặng có thể nguy hiểm tính mạng: cần đưa đi khám ngay.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('aglepristone', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Không dùng khi cần giữ thai.', 0),
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Giun sán đường ruột - chó', 'giun san duong ruot - cho', 'Giun sán đường ruột ở chó: bụng to, tiêu chảy, thấy giun trong phân.', 'Tẩy giun sán, vệ sinh nơi ở.', 'Tẩy lại sau 2-3 tuần và tẩy giun định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('praziquantel', 'Theo cân nặng', '1 lần', NULL, 'Kiểm tra tuổi tối thiểu theo nhãn.', 0),
    ('pyrantel', 'Theo cân nặng', '1 lần', NULL, NULL, 1),
    ('fenbendazole', 'Theo cân nặng', '1 lần/ngày', '3', NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Bệnh van tim (MMVD) - chó', 'benh van tim (mmvd) - cho', 'Bệnh van tim (MMVD) ở chó: ho, khó thở, mệt khi vận động.', 'Dùng thuốc hỗ trợ tim và lợi tiểu theo giai đoạn bệnh; hạn chế vận động mạnh.', 'Bệnh mạn tính, dùng thuốc lâu dài. Theo dõi nhịp thở lúc nghỉ tại nhà; tái khám siêu âm tim định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('pimobendan', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống theo hướng dẫn trên nhãn.', 0),
    ('furosemide', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Để thú uống nước tự do.', 1),
    ('benazepril', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Suy thận mạn - mèo', 'suy than man - meo', 'Suy thận mạn ở mèo: uống nhiều, gầy, nôn, hôi miệng.', 'Kiểm soát huyết áp và protein niệu, truyền dịch khi cần, ăn thức ăn chuyên cho thận.', 'Bệnh mạn tính. Xét nghiệm thận định kỳ; luôn cho mèo uống nước đầy đủ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('benazepril', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0),
    ('telmisartan (semintra)', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Cường giáp - mèo', 'cuong giap - meo', 'Cường giáp ở mèo: ăn nhiều nhưng gầy, hiếu động, nôn.', 'Dùng thuốc kháng giáp hoặc chuyển sang thức ăn chuyên biệt theo chỉ định.', 'Xét nghiệm T4, men gan và công thức máu định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('methimazole', 'Theo cân nặng', '2 lần/ngày', NULL, 'Báo bác sĩ nếu mèo nôn, ngứa mặt hoặc bỏ ăn.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Tiểu đường - mèo', 'tieu duong - meo', 'Tiểu đường ở mèo: uống nhiều, tiểu nhiều, đi lệch chân sau.', 'Tiêm insulin đều đặn kết hợp chế độ ăn ít tinh bột.', 'Chủ nuôi cần học cách tiêm và nhận biết hạ đường huyết. Tái khám đo đường huyết định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('insulin glargine', 'Theo cân nặng', 'Theo lịch tiêm bác sĩ hướng dẫn', NULL, 'Không tự đổi liều.', 0),
    ('prozinc', 'Theo cân nặng', 'Theo lịch tiêm bác sĩ hướng dẫn', NULL, 'Dùng thay cho Insulin Glargine, không dùng cùng lúc.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Hen suyễn - mèo', 'hen suyen - meo', 'Hen suyễn ở mèo: ho, thở khò khè, thở há miệng.', 'Giãn phế quản và kháng viêm đường hô hấp; hạn chế bụi, khói, cát vệ sinh nhiều bụi.', 'Thở há miệng là cấp cứu: đưa đi khám ngay.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('fluticasone (xit)', 'Theo hướng dẫn của bác sĩ', '2 lần/ngày', NULL, 'Dùng qua buồng hít chuyên dụng cho mèo.', 0),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Không tự ngừng đột ngột.', 1),
    ('terbutaline', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Thận trọng ở mèo bệnh tim.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Toxoplasma - mèo', 'toxoplasma - meo', 'Toxoplasma ở mèo: sốt, viêm mắt, triệu chứng thần kinh.', 'Kháng sinh diệt ký sinh trùng; điều trị hỗ trợ.', 'Bệnh lây sang người, nhất là phụ nữ mang thai: vệ sinh kỹ khi chăm sóc, đeo găng khi dọn khay cát.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('clindamycin', 'Theo cân nặng', '2 lần/ngày', NULL, NULL, 0),
    ('trimethoprim-sulfa', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Thiếu máu do Mycoplasma haemofelis - mèo', 'thieu mau do mycoplasma haemofelis - meo', 'Thiếu máu do Mycoplasma haemofelis ở mèo: lợi nhợt, yếu, sốt.', 'Kháng sinh kết hợp thuốc ức chế miễn dịch khi cần; truyền máu nếu thiếu máu nặng.', 'Xét nghiệm công thức máu để theo dõi. Cho mèo uống thuốc đủ liệu trình.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('doxycycline', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống kèm nước hoặc thức ăn để tránh viêm thực quản.', 0),
    ('pradofloxacin', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 1),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Không tự ngừng đột ngột.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Rận tai (Otodectes) - mèo', 'ran tai (otodectes) - meo', 'Rận tai ở mèo: gãi tai, ráy tai đen như bã cà phê.', 'Diệt ký sinh trùng; vệ sinh tai.', 'Điều trị cả các thú khác sống chung. Tái khám sau 2-4 tuần.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('selamectin (revolution)', 'Theo nhãn sản phẩm', '1 lần', NULL, 'Nhỏ lên da vùng gáy.', 0),
    ('moxidectin (advocate)', 'Theo nhãn sản phẩm', '1 lần', NULL, 'Dùng thay cho Selamectin, không dùng cùng lúc.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Bọ chét - mèo', 'bo chet - meo', 'Bọ chét ở mèo: ngứa, cắn lưng, viêm da dị ứng do nước bọt bọ chét.', 'Diệt bọ chét trên thú và xử lý môi trường sống.', 'Điều trị tất cả thú trong nhà và vệ sinh ổ nằm.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('selamectin (revolution)', 'Theo nhãn sản phẩm', '1 lần/tháng', NULL, NULL, 0),
    ('fluralaner (bravecto)', 'Theo nhãn sản phẩm', 'Theo nhãn sản phẩm', NULL, 'Dùng thay cho Selamectin, không dùng cùng lúc.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Búi lông - mèo', 'bui long - meo', 'Búi lông ở mèo: nôn ra lông, táo bón.', 'Thuốc nhuận tràng giúp thải lông; chải lông thường xuyên.', 'Đưa đi khám nếu mèo nôn nhiều, bỏ ăn hoặc không đi tiêu.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('gel laxatone', 'Theo nhãn sản phẩm', '1 lần/ngày', NULL, NULL, 0),
    ('malt paste', 'Theo nhãn sản phẩm', '1 lần/ngày', NULL, 'Dùng thay cho Gel Laxatone, không dùng cùng lúc.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Giardia - mèo', 'giardia - meo', 'Giardia ở mèo: tiêu chảy kéo dài, phân nhầy mùi hôi.', 'Diệt ký sinh trùng, vệ sinh khay cát và nơi ở.', 'Giặt ổ nằm và tắm cho mèo để tránh tái nhiễm. Tái khám xét nghiệm phân.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('metronidazole', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('fenbendazole', 'Theo cân nặng', '1 lần/ngày', '5', NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Thiếu vitamin A - chim cảnh', 'thieu vitamin a - chim canh', 'Thiếu vitamin A ở chim cảnh: nốt trắng trong miệng, chảy mũi.', 'Bổ sung vitamin A và điều chỉnh khẩu phần (rau củ, thức ăn viên).', 'Thừa vitamin A cũng gây ngộ độc: dùng đúng liều bác sĩ chỉ định.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('vitamin a', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Nấm diều (Candida) - chim cảnh', 'nam dieu (candida) - chim canh', 'Nấm diều (Candida) ở chim cảnh: nôn, diều căng, mỏ có mảng trắng.', 'Thuốc kháng nấm; vệ sinh dụng cụ ăn uống.', 'Tái khám nếu chim tiếp tục nôn hoặc bỏ ăn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('nystatin', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('fluconazole', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Megabacteria (Macrorhabdus) - chim cảnh', 'megabacteria (macrorhabdus) - chim canh', 'Megabacteria (Macrorhabdus) ở chim cảnh: gầy, nôn, phân có hạt nguyên.', 'Thuốc kháng nấm đường uống; hỗ trợ dinh dưỡng.', 'Chim yếu cần giữ ấm và theo dõi sát.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('amphotericin b', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Dùng đường uống theo chỉ định bác sĩ.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('CRD (Mycoplasma) - chim cảnh', 'crd (mycoplasma) - chim canh', 'CRD (Mycoplasma) ở chim cảnh: hắt hơi, sưng mắt, thở khò khè.', 'Kháng sinh; giữ ấm, thông thoáng, tránh gió lùa.', 'Cách ly chim bệnh với chim khỏe.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('tylosin', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('doxycycline', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1),
    ('enrofloxacin', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Đậu chim (Avian Pox) - chim cảnh', 'dau chim (avian pox) - chim canh', 'Đậu chim (Avian Pox): nốt sần ở mỏ, chân, quanh mắt.', 'Không có thuốc đặc trị; sát trùng nốt và bổ sung vitamin A, chăm sóc hỗ trợ.', 'Bệnh lây giữa các con: cách ly chim bệnh. Có thể bội nhiễm, cần theo dõi.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('povidone-iodine', 'Pha loãng theo hướng dẫn', '1-2 lần/ngày', NULL, 'Chấm lên nốt, tránh vùng mắt.', 0),
    ('vitamin a', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Trùng roi (Trichomonas) - chim cảnh', 'trung roi (trichomonas) - chim canh', 'Trùng roi (Trichomonas) ở chim cảnh: nuốt khó, mảng vàng trong họng, chảy dãi.', 'Thuốc diệt trùng roi; vệ sinh nước uống và dụng cụ ăn.', 'Điều trị cả đàn nếu nuôi chung.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('metronidazole', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('ronidazole', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1),
    ('carnidazole', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Nhiễm khuẩn E. coli, Salmonella - chim cảnh', 'nhiem khuan e. coli, salmonella - chim canh', 'Nhiễm khuẩn E. coli, Salmonella ở chim cảnh: tiêu chảy, phân xanh lỏng, xù lông.', 'Kháng sinh theo chỉ định; bù nước, giữ ấm, vệ sinh lồng.', 'Salmonella có thể lây sang người: rửa tay kỹ sau khi chăm sóc.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('enrofloxacin', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('trimethoprim-sulfa', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Kẹt trứng - chim cảnh', 'ket trung - chim canh', 'Kẹt trứng ở chim cảnh: rặn liên tục, đứng dưới đáy lồng, bụng phình.', 'Sưởi ấm, bổ sung canxi; dùng thuốc co bóp tử cung khi bác sĩ xác định phù hợp.', 'Cấp cứu: để chim ở nơi ấm, yên tĩnh và đưa đi khám ngay. Không tự bóp bụng chim.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('calcium gluconate', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Tiêm theo chỉ định bác sĩ.', 0),
    ('oxytocin', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Chỉ dùng theo chỉ định của bác sĩ.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;
