-- Don thuoc mau cho nhom benh 71-120 (mat, ung thu, mien dich, noi tiet, tim mach, hanh vi, chim
-- canh), dung thuoc trong V7, V9 va V11. Cung quy uoc nhu V8: lieu ghi "theo can nang / theo nhan /
-- theo chi dinh" chu khong co so mg/kg; created_by la UUID rong (du lieu he thong); ON CONFLICT DO
-- NOTHING theo search_name; thuoc nao khong co trong danh muc thi dong do khong duoc tao.

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm kết mạc - chó, mèo', 'viem ket mac - cho, meo', 'Viêm kết mạc: mắt đỏ, nhiều ghèn, chảy nước mắt.', 'Làm sạch ghèn quanh mắt; nhỏ hoặc tra thuốc mắt kháng sinh theo chỉ định.', 'Không để thú dụi hoặc cào mắt. Tái khám nếu mắt đục, đau nhiều hoặc không đỡ sau vài ngày.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('tobramycin (nho mat)', 'Theo nhãn sản phẩm', '3-4 lần/ngày', NULL, 'Bác sĩ chọn một trong các thuốc mắt.', 0),
    ('chloramphenicol (nho mat)', 'Theo nhãn sản phẩm', '3-4 lần/ngày', NULL, 'Dùng thay cho Tobramycin.', 1),
    ('oxytetracycline (mo mat)', 'Theo nhãn sản phẩm', '2-3 lần/ngày', NULL, 'Tra mắt buổi tối.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Đục thủy tinh thể - sau mổ - chó, mèo', 'duc thuy tinh the - sau mo - cho, meo', 'Đục thủy tinh thể: mắt đục trắng, va vấp đồ vật. Chăm sóc sau phẫu thuật Phaco.', 'Chống viêm sau mổ bằng thuốc nhỏ mắt theo chỉ định.', 'Đeo vòng chống liếm, không để thú dụi mắt. Tái khám đúng hẹn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone acetate (nho mat)', 'Theo nhãn sản phẩm', '3-4 lần/ngày', NULL, NULL, 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Tăng nhãn áp (Glaucoma) - chó', 'tang nhan ap (glaucoma) - cho', 'Tăng nhãn áp: mắt đỏ, giãn đồng tử, đau mắt.', 'Hạ nhãn áp bằng thuốc nhỏ mắt; theo dõi nhãn áp định kỳ.', 'Cấp cứu mắt: tăng nhãn áp lâu có thể gây mù. Tái khám đo nhãn áp theo hẹn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('dorzolamide (nho mat)', 'Theo nhãn sản phẩm', '2-3 lần/ngày', NULL, NULL, 0),
    ('timolol (nho mat)', 'Theo nhãn sản phẩm', '2 lần/ngày', NULL, NULL, 1),
    ('latanoprost (nho mat)', 'Theo nhãn sản phẩm', '1 lần/ngày', NULL, 'Chủ yếu dùng cho chó.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm tuyến hậu môn - chó, mèo', 'viem tuyen hau mon - cho, meo', 'Viêm tuyến hậu môn: lết mông, liếm hậu môn, sưng.', 'Nặn tuyến hậu môn; kháng sinh và giảm đau kháng viêm.', 'Tái khám nếu sưng nóng, rò mủ hoặc thú đau khi đi ngoài.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Áp xe do cắn nhau - chó, mèo', 'ap xe do can nhau - cho, meo', 'Áp xe do cắn nhau: sưng nóng, có mủ, sốt.', 'Rạch dẫn lưu mủ, rửa vết thương; dùng kháng sinh.', 'Giữ vết thương sạch, không để thú liếm. Tái khám để thay băng và kiểm tra dẫn lưu.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('clindamycin', 'Theo cân nặng', '2 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thêm hoặc thay thế.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Say nóng (Heat stroke) - chó, mèo', 'say nong (heat stroke) - cho, meo', 'Say nóng: thở dốc, lưỡi đỏ thẫm, lả, nôn.', 'Cấp cứu: hạ nhiệt từ từ bằng nước thường, truyền dịch, thở oxy.', 'Cấp cứu: không dùng nước đá để làm mát. Đưa đi khám ngay.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('dich truyen', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm phổi do vi khuẩn - chó, mèo', 'viem phoi do vi khuan - cho, meo', 'Viêm phổi do vi khuẩn: ho, sốt, thở nhanh.', 'Kháng sinh, khí dung làm loãng đờm; nghỉ ngơi nơi thoáng mát, tránh vận động mạnh.', 'Tái khám nếu thở khó, tím tái hoặc bỏ ăn. Cho uống đủ liệu trình kháng sinh.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('doxycycline', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Cho uống cùng thức ăn và nước để tránh loét thực quản (đặc biệt ở mèo).', 1),
    ('nuoc muoi sinh ly 0,9%', 'Theo chỉ định của bác sĩ', 'Khí dung 2-3 lần/ngày', NULL, 'Dùng để khí dung làm loãng đờm.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Vết thương, bỏng ngoài da - chó, mèo', 'vet thuong, bong ngoai da - cho, meo', 'Vết thương, bỏng ngoài da: da tổn thương, rỉ dịch.', 'Làm sạch vết thương, sát khuẩn, bôi thuốc tại chỗ và băng; kháng sinh khi nhiễm trùng.', 'Giữ vết thương sạch và khô, đeo vòng chống liếm. Tái khám nếu sưng đỏ, có mủ hoặc sốt.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('silver sulfadiazine', 'Theo nhãn sản phẩm', '1-2 lần/ngày', NULL, 'Bôi lên vết thương đã làm sạch.', 0),
    ('povidone-iodine', 'Theo nhãn sản phẩm', '1-2 lần/ngày', NULL, 'Dùng pha loãng để rửa vết thương.', 1),
    ('cephalexin', 'Theo cân nặng', '2 lần/ngày', NULL, 'Dùng khi vết thương nhiễm trùng. Cho uống cùng thức ăn.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('U tế bào mast - chó', 'u te bao mast - cho', 'U tế bào mast: khối u da, có thể đỏ và sưng lên.', 'Phẫu thuật cắt u; dùng thuốc kháng viêm, thuốc điều trị đích theo chỉ định.', 'Thuốc này do bác sĩ thú y chuyên khoa chỉ định và theo dõi xét nghiệm định kỳ. Không bóp hoặc chà xát khối u.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 0),
    ('toceranib (palladia)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Chỉ dùng khi bác sĩ chuyên khoa chỉ định.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Lymphoma - chó, mèo', 'lymphoma - cho, meo', 'Lymphoma: hạch to, sụt cân, mệt.', 'Hoá trị theo phác đồ CHOP kèm Prednisolone.', 'Thuốc này do bác sĩ thú y chuyên khoa chỉ định và theo dõi xét nghiệm định kỳ. Theo dõi công thức máu trước mỗi đợt hoá trị.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('cyclophosphamide', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Thuộc phác đồ CHOP.', 0),
    ('doxorubicin', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Thuộc phác đồ CHOP.', 1),
    ('vincristine', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Thuộc phác đồ CHOP.', 2),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 3)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Giun phổi - chó, mèo', 'giun phoi - cho, meo', 'Giun phổi: ho mạn tính, khó thở.', 'Tẩy giun phổi; có thể lặp lại theo chỉ định.', 'Tái khám sau khi tẩy để kiểm tra lại; tẩy giun định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('fenbendazole', 'Theo cân nặng', '1 lần/ngày', 5, 'Cho uống cùng thức ăn.', 0),
    ('moxidectin (advocate)', 'Theo nhãn sản phẩm', 'Theo chỉ định của bác sĩ', NULL, 'Dạng nhỏ gáy, dùng theo nhãn.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Thiếu máu tan huyết miễn dịch (IMHA) - chó, mèo', 'thieu mau tan huyet mien dich (imha) - cho, meo', 'Thiếu máu tan huyết miễn dịch: lợi nhợt, vàng da, nước tiểu sẫm.', 'Ức chế miễn dịch; theo dõi hồng cầu và chức năng gan, thận.', 'Thuốc này do bác sĩ thú y chuyên khoa chỉ định và theo dõi xét nghiệm định kỳ. Không tự ngừng thuốc.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Không tự ngừng đột ngột.', 0),
    ('mycophenolate', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1),
    ('cyclosporine', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Giảm tiểu cầu miễn dịch (ITP) - chó, mèo', 'giam tieu cau mien dich (itp) - cho, meo', 'Giảm tiểu cầu miễn dịch: chấm xuất huyết, chảy máu cam.', 'Ức chế miễn dịch; theo dõi số lượng tiểu cầu.', 'Thuốc này do bác sĩ thú y chuyên khoa chỉ định và theo dõi xét nghiệm định kỳ. Hạn chế vận động mạnh, tránh va chạm khi tiểu cầu thấp.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Không tự ngừng đột ngột.', 0),
    ('vincristine', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Chỉ dùng khi bác sĩ chuyên khoa chỉ định.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Hạ đường huyết - chó, mèo con, giống nhỏ', 'ha duong huyet - cho, meo con, giong nho', 'Hạ đường huyết: run, lờ đờ, co giật.', 'Bổ sung đường: truyền dextrose hoặc bôi nướu; cho ăn sau khi tỉnh.', 'Cấp cứu nếu thú co giật hoặc lịm đi. Chia nhiều bữa ăn nhỏ trong ngày.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('dextrose (glucose)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Truyền chậm, theo dõi đường huyết.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Sản giật (mẹ sau sinh) - chó, mèo', 'san giat (me sau sinh) - cho, meo', 'Sản giật: run, thở gấp, co cứng cơ ở mẹ sau sinh.', 'Bổ sung canxi đường tĩnh mạch rồi duy trì bằng canxi đường uống; tạm tách con khỏi mẹ.', 'Cấp cứu: đưa đi khám ngay. Truyền canxi phải theo dõi nhịp tim.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('calcium gluconate', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Truyền tĩnh mạch chậm, theo dõi tim.', 0),
    ('canxi (duong uong)', 'Theo cân nặng', '2-3 lần/ngày', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Cầu trùng - chó, mèo con', 'cau trung - cho, meo con', 'Cầu trùng: tiêu chảy, có thể lẫn máu.', 'Diệt cầu trùng; bù nước; vệ sinh chuồng, ổ nằm.', 'Vệ sinh chuồng hằng ngày, tách thú bệnh. Tái khám nếu mất nước, bỏ ăn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('toltrazuril (baycox)', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0),
    ('sulfadimethoxine (albon)', 'Theo cân nặng', '1 lần/ngày', NULL, 'Bác sĩ chọn một trong các thuốc diệt cầu trùng.', 1),
    ('ponazuril', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Hội chứng mũi ngắn - chó (Pug, Bulldog)', 'hoi chung mui ngan - cho (pug, bulldog)', 'Hội chứng mũi ngắn: ngáy, thở khò khè, dễ ngất khi nóng.', 'Giảm cân; phẫu thuật mở rộng mũi, cắt ngắn vòm họng khi cần; chống viêm.', 'Tránh nơi nóng và vận động mạnh. Giữ cân nặng hợp lý.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng ngắn ngày theo chỉ định.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Xẹp khí quản - chó giống nhỏ', 'xep khi quan - cho giong nho', 'Xẹp khí quản: ho như tiếng ngỗng kêu.', 'Giảm ho, giãn phế quản, chống viêm; kiểm soát cân nặng, dùng dây ngực thay vòng cổ.', 'Dùng dây ngực thay vòng cổ. Tránh kích thích, nơi nóng hoặc bụi.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('butorphanol', 'Theo cân nặng', '2-3 lần/ngày', NULL, 'Giảm ho; có thể gây an thần.', 0),
    ('theophylline', 'Theo cân nặng', '2 lần/ngày', NULL, 'Không tự tăng liều.', 1),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng ngắn ngày theo chỉ định.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Đứt dây chằng chéo trước - chó', 'dut day chang cheo truoc - cho', 'Đứt dây chằng chéo trước: đột ngột khập khiễng chân sau.', 'Giảm đau, chống viêm; phẫu thuật ổn định khớp (TPLO) khi cần; hạn chế vận động.', 'Nghỉ ngơi, hạn chế chạy nhảy, leo cầu thang. Giữ cân nặng hợp lý.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('carprofen', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('gabapentin', 'Theo cân nặng', '2-3 lần/ngày', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Trật xương bánh chè - chó', 'trat xuong banh che - cho', 'Trật xương bánh chè: đi nhảy cóc, co chân sau.', 'Giảm đau, chống viêm, bổ sung glucosamine; phẫu thuật khi nặng.', 'Hạn chế nhảy, leo cầu thang. Giữ cân nặng hợp lý.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('glucosamine', 'Theo nhãn sản phẩm', '1 lần/ngày', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Addison (suy vỏ thượng thận) - chó', 'addison (suy vo thuong than) - cho', 'Addison: nôn, yếu, có thể suy sụp đột ngột.', 'Thay thế hormone thượng thận; theo dõi điện giải định kỳ.', 'Suy sụp đột ngột là cấp cứu. Không tự ngừng thuốc. Xét nghiệm điện giải theo hẹn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('docp (zycortal)', 'Theo chỉ định của bác sĩ', 'Khoảng 25 ngày/lần', NULL, 'Tiêm theo chỉ định.', 0),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Không tự ngừng đột ngột.', 1),
    ('fludrocortisone', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay cho DOCP.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Bệnh cơ tim giãn (DCM) - chó', 'benh co tim gian (dcm) - cho', 'Bệnh cơ tim giãn: mệt, ho, bụng chướng nước.', 'Tăng sức co bóp tim, lợi tiểu, giãn mạch; theo dõi siêu âm tim.', 'Hạn chế vận động mạnh; theo dõi nhịp thở lúc nghỉ. Tái khám định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('pimobendan', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống lúc đói, trước bữa ăn khoảng 1 giờ.', 0),
    ('furosemide', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 1),
    ('benazepril', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Bệnh tuyến tiền liệt - chó', 'benh tuyen tien liet - cho', 'Bệnh tuyến tiền liệt: tiểu khó, đi ngoài khó.', 'Giảm kích thước tuyến, kháng sinh khi nhiễm trùng; thiến để phòng tái phát.', 'Tái khám để kiểm tra lại kích thước tuyến. Thiến là cách phòng tái phát hiệu quả.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('osaterone (ypozane)', 'Theo cân nặng', '1 lần/ngày', NULL, 'Người cầm thuốc rửa tay sau khi dùng.', 0),
    ('enrofloxacin', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng khi có nhiễm trùng.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Nấm Malassezia - chó', 'nam malassezia - cho', 'Nấm Malassezia: da nhờn, mùi hôi, ngứa.', 'Tắm thuốc kháng nấm và dùng thuốc uống kháng nấm theo chỉ định.', 'Tắm đúng lịch, để dầu tắm đủ thời gian rồi xả sạch. Tái khám để đánh giá lại.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('ketoconazole', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('itraconazole', 'Theo cân nặng', '1 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay cho Ketoconazole.', 1),
    ('dau tam malaseb', 'Theo nhãn sản phẩm', '2-3 lần/tuần', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('U hạt do liếm (Lick granuloma) - chó', 'u hat do liem (lick granuloma) - cho', 'U hạt do liếm: liếm một chỗ liên tục, vết loét dày.', 'Điều trị nhiễm trùng thứ phát, giảm hành vi liếm; tìm nguyên nhân (đau, dị ứng, stress).', 'Đeo vòng chống liếm. Tăng vận động và đồ chơi để giảm stress.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('cephalexin', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('fluoxetine', 'Theo cân nặng', '1 lần/ngày', NULL, 'Có thể mất vài tuần mới thấy hiệu quả.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Giãn thực quản (Megaesophagus) - chó', 'gian thuc quan (megaesophagus) - cho', 'Giãn thực quản: trớ thức ăn sau khi ăn, viêm phổi hít.', 'Cho ăn ở tư thế đứng (ghế Bailey); thuốc hỗ trợ nhu động.', 'Cho ăn ở tư thế đứng và giữ nguyên tư thế 10-15 phút sau ăn. Theo dõi dấu hiệu viêm phổi hít.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('sildenafil', 'Theo cân nặng', '2-3 lần/ngày', NULL, NULL, 0),
    ('metoclopramide', 'Theo cân nặng', '2-3 lần/ngày', NULL, 'Cho uống trước bữa ăn.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Khô mắt (KCS) - chó', 'kho mat (kcs) - cho', 'Khô mắt: ghèn đặc, giác mạc khô, mờ.', 'Kích thích tiết nước mắt bằng thuốc nhỏ mắt dùng lâu dài; bổ sung nước mắt nhân tạo.', 'Thường phải dùng lâu dài, không tự ngừng. Làm sạch ghèn trước khi nhỏ thuốc.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('cyclosporine (nho mat)', 'Theo nhãn sản phẩm', '2 lần/ngày', NULL, NULL, 0),
    ('tacrolimus (nho mat)', 'Theo nhãn sản phẩm', '2 lần/ngày', NULL, 'Dùng khi Cyclosporine không đủ hiệu quả.', 1),
    ('nuoc mat nhan tao', 'Theo nhãn sản phẩm', '3-4 lần/ngày', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm miệng mạn tính (FCGS) - mèo', 'viem mieng man tinh (fcgs) - meo', 'Viêm miệng mạn tính: đau miệng nặng, chảy dãi, bỏ ăn.', 'Chống viêm, điều hoà miễn dịch; nhổ răng khi cần; chăm sóc răng miệng.', 'Tái khám nếu mèo bỏ ăn quá 24 giờ. Chăm sóc răng miệng theo hướng dẫn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0),
    ('interferon omega (virbagen omega)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Tăng huyết áp - mèo', 'tang huyet ap - meo', 'Tăng huyết áp: mù đột ngột, đồng tử giãn.', 'Hạ huyết áp bằng thuốc; theo dõi huyết áp và tìm bệnh nền (thận, cường giáp).', 'Theo dõi huyết áp định kỳ. Mù đột ngột là cấp cứu.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('amlodipine', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0),
    ('telmisartan (semintra)', 'Theo cân nặng', '1 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay hoặc kết hợp.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm đường mật (Cholangitis) - mèo', 'viem duong mat (cholangitis) - meo', 'Viêm đường mật: vàng da, sốt, bỏ ăn.', 'Hỗ trợ gan mật, kháng sinh và chống viêm theo chỉ định; dinh dưỡng hỗ trợ.', 'Tái khám nếu bỏ ăn quá 24 giờ. Mèo bỏ ăn có nguy cơ gan nhiễm mỡ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('ursodiol', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 1),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng khi bác sĩ chỉ định.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm mũi mạn tính - mèo', 'viem mui man tinh - meo', 'Viêm mũi mạn tính: chảy mũi, hắt hơi kéo dài.', 'Khí dung nước muối, kháng sinh và chống viêm theo chỉ định; vệ sinh mũi.', 'Lau sạch dịch mũi. Tái khám nếu chảy mũi có máu hoặc bỏ ăn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('doxycycline', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Cho uống cùng thức ăn và nước để tránh loét thực quản.', 0),
    ('nuoc muoi sinh ly 0,9%', 'Theo chỉ định của bác sĩ', 'Khí dung 2-3 lần/ngày', NULL, NULL, 1),
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng ngắn ngày theo chỉ định.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Rụng lông do liếm quá mức (stress) - mèo', 'rung long do liem qua muc (stress) - meo', 'Rụng lông do liếm quá mức: liếm trụi lông vùng bụng, đùi.', 'Giảm stress và làm phong phú môi trường; dùng thuốc ổn định tâm lý khi cần.', 'Cần loại trừ nguyên nhân khác (ngứa, ký sinh trùng, đau) trước khi dùng thuốc. Thêm đồ chơi, chỗ trèo, chỗ trốn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('fluoxetine', 'Theo cân nặng', '1 lần/ngày', NULL, 'Có thể mất vài tuần mới thấy hiệu quả.', 0),
    ('clomipramine', 'Theo cân nặng', '1 lần/ngày', NULL, 'Không dùng chung với Fluoxetine.', 1),
    ('feliway (pheromone)', 'Theo nhãn sản phẩm', 'Liên tục', NULL, 'Dạng khuếch tán trong phòng.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Ung thư biểu mô vảy (mèo lông trắng)', 'ung thu bieu mo vay (meo long trang)', 'Ung thư biểu mô vảy: loét ở vành tai, mũi không lành.', 'Phẫu thuật cắt, xạ trị; giảm đau chống viêm.', 'Cho mèo lông trắng tránh nắng. Tái khám sớm khi có vết loét lâu lành.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Phức hợp u hạt bạch cầu ái toan - mèo', 'phuc hop u hat bach cau ai toan - meo', 'Phức hợp u hạt bạch cầu ái toan: loét môi trên, mảng đỏ ngứa.', 'Chống viêm và ức chế miễn dịch; tìm nguyên nhân dị ứng.', 'Tìm và loại bỏ nguyên nhân dị ứng (bọ chét, thức ăn, môi trường).', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0),
    ('methylprednisolone acetate', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Dạng tiêm tác dụng kéo dài, không dùng chung với NSAID.', 1),
    ('cyclosporine', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng khi corticoid không đủ hoặc không dùng được.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Bartonella (bệnh mèo cào) - mèo', 'bartonella (benh meo cao) - meo', 'Bartonella: sốt, sưng hạch, thường không rõ triệu chứng.', 'Dùng kháng sinh kéo dài theo chỉ định.', 'Cần dùng đủ liệu trình kháng sinh. Bắt bọ chét định kỳ vì bọ chét lây bệnh.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('doxycycline', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Cho uống cùng thức ăn và nước để tránh loét thực quản.', 0),
    ('azithromycin', 'Theo cân nặng', '1 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay thế.', 1),
    ('pradofloxacin', 'Theo cân nặng', '1 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay thế.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Polyp mũi họng - mèo (sau mổ)', 'polyp mui hong - meo (sau mo)', 'Polyp mũi họng: thở khò khè, nghiêng đầu, viêm tai.', 'Phẫu thuật lấy polyp; dùng corticoid ngắn ngày sau mổ để giảm tái phát.', 'Tái khám đúng hẹn; polyp có thể tái phát.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng ngắn ngày sau mổ theo chỉ định.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm xoang - chim cảnh', 'viem xoang - chim canh', 'Viêm xoang: sưng quanh mắt, chảy mũi.', 'Kháng sinh, rửa xoang bằng nước muối.', 'Giữ chuồng sạch, thoáng, tránh gió lùa. Tái khám nếu sưng nhiều hoặc chim bỏ ăn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('enrofloxacin', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 0),
    ('doxycycline', 'Theo cân nặng', '1 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay thế.', 1),
    ('nuoc muoi sinh ly 0,9%', 'Theo chỉ định của bác sĩ', 'Rửa xoang theo hướng dẫn', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Giãn dạ dày tuyến (PDD, Bornavirus) - chim cảnh', 'gian da day tuyen (pdd, bornavirus) - chim canh', 'Giãn dạ dày tuyến: nôn, gầy, phân có hạt nguyên.', 'Chống viêm hỗ trợ và chăm sóc dinh dưỡng; bệnh khó chữa dứt điểm.', 'Cho ăn thức ăn mềm, dễ tiêu; tách chim bệnh khỏi đàn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0),
    ('celecoxib', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Không dùng chung với Meloxicam.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm gan bàn chân (Bumblefoot) - chim cảnh', 'viem gan ban chan (bumblefoot) - chim canh', 'Bumblefoot: gan bàn chân sưng đỏ, đóng vảy.', 'Làm sạch, kháng sinh, bôi thuốc tại chỗ; thay cầu đậu mềm.', 'Thay cầu đậu bằng loại mềm, đường kính phù hợp. Giữ chuồng khô sạch.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('enrofloxacin', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 0),
    ('silver sulfadiazine', 'Theo nhãn sản phẩm', '1-2 lần/ngày', NULL, 'Bôi lên vết thương đã làm sạch.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Gãy chân, gãy cánh - chim cảnh', 'gay chan, gay canh - chim canh', 'Gãy chân, gãy cánh: không đứng được, cánh xệ.', 'Nẹp cố định; giảm đau chống viêm; hạn chế vận động.', 'Nhốt ở nơi yên tĩnh, thấp, không có cầu đậu cao.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Mạt mỏ (Knemidocoptes) - chim cảnh', 'mat mo (knemidocoptes) - chim canh', 'Mạt mỏ: mỏ và chân xốp như tổ ong.', 'Diệt mạt; vệ sinh chuồng và đồ vật.', 'Vệ sinh, khử trùng chuồng và đồ dùng. Tách chim bệnh khỏi đàn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('ivermectin', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Liều chim rất thấp: chỉ dùng đúng chỉ định.', 0),
    ('moxidectin (advocate)', 'Theo nhãn sản phẩm', 'Theo chỉ định của bác sĩ', NULL, 'Bác sĩ cân nhắc dùng thay Ivermectin.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Đẻ trứng liên tục - chim cảnh', 'de trung lien tuc - chim canh', 'Đẻ trứng liên tục: đẻ nhiều lứa, thiếu canxi.', 'Giảm kích thích đẻ: giảm thời gian chiếu sáng; dùng thuốc cấy hoặc tiêm ức chế hormone sinh sản.', 'Giảm thời gian chiếu sáng xuống khoảng 8-10 giờ/ngày, bỏ ổ đẻ. Bổ sung canxi theo chỉ định.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('deslorelin (suprelorin)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Cấy dưới da theo chỉ định.', 0),
    ('leuprolide', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Bác sĩ cân nhắc dùng thay thế.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Gan nhiễm mỡ - chim cảnh', 'gan nhiem mo - chim canh', 'Gan nhiễm mỡ ở chim: béo, mỏ và móng mọc dài, khó thở.', 'Hỗ trợ gan; điều chỉnh khẩu phần, giảm hạt hướng dương.', 'Giảm hạt hướng dương, tăng rau củ; tăng vận động.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('silymarin', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 0),
    ('lactulose', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Nhổ lông (Feather plucking) - chim cảnh', 'nho long (feather plucking) - chim canh', 'Nhổ lông: tự nhổ lông, da trụi.', 'Tìm nguyên nhân (đau, ngứa, stress); làm phong phú môi trường; dùng thuốc ổn định tâm lý khi cần.', 'Cần loại trừ bệnh da, ký sinh trùng trước khi dùng thuốc. Thêm đồ chơi, thời gian tương tác.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('haloperidol', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Chỉ dùng khi bác sĩ chỉ định.', 0),
    ('clomipramine', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Bác sĩ cân nhắc dùng thay thế.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm kết mạc - chim cảnh', 'viem ket mac - chim canh', 'Viêm kết mạc ở chim: mắt sưng, nhắm mắt, chảy nước.', 'Nhỏ thuốc mắt kháng sinh; kháng sinh toàn thân khi nhiễm trùng hô hấp kèm theo.', 'Giữ chuồng sạch, tránh bụi và gió lùa. Tái khám nếu mắt sưng nhiều hoặc đục.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('ciprofloxacin (nho mat)', 'Theo nhãn sản phẩm', '3-4 lần/ngày', NULL, NULL, 0),
    ('tobramycin (nho mat)', 'Theo nhãn sản phẩm', '3-4 lần/ngày', NULL, 'Dùng thay cho Ciprofloxacin.', 1),
    ('doxycycline', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng khi có nhiễm trùng toàn thân.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;
