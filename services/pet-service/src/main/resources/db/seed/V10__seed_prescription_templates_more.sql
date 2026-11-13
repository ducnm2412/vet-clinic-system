-- Don thuoc mau cho nhom benh 51-70 (tieu hoa, than kinh, co xuong khop, tim mach, mat, da, chim
-- canh), dung thuoc trong V7 va V9. Cung quy uoc nhu V8: lieu ghi "theo can nang / theo nhan / theo
-- chi dinh" chu khong co so mg/kg; created_by la UUID rong (du lieu he thong); ON CONFLICT DO NOTHING
-- theo search_name; thuoc nao khong co trong danh muc thi dong do khong duoc tao.

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm dạ dày cấp - chó, mèo', 'viem da day cap - cho, meo', 'Viêm dạ dày cấp: nôn, bỏ ăn, đau bụng, liếm môi.', 'Giảm tiết acid, chống nôn; cho nhịn ăn ngắn rồi ăn lại thức ăn dễ tiêu theo chỉ định.', 'Tái khám nếu nôn kéo dài, nôn ra máu hoặc bỏ ăn quá 24 giờ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('omeprazole', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống lúc đói.', 0),
    ('famotidine', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay cho Omeprazole.', 1),
    ('maropitant (cerenia)', 'Theo cân nặng', '1 lần/ngày', NULL, 'Kiểm tra độ tuổi tối thiểu theo nhãn.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Loét dạ dày - chó, mèo', 'loet da day - cho, meo', 'Loét dạ dày: nôn ra máu, phân đen, đau vùng bụng trên.', 'Giảm tiết acid và bảo vệ niêm mạc dạ dày; tìm và loại bỏ nguyên nhân (thuốc kháng viêm, stress, bệnh nền).', 'Ngưng các thuốc kháng viêm NSAID hoặc corticoid nếu bác sĩ chỉ định. Nôn ra máu nhiều hoặc phân đen kéo dài cần khám ngay.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('omeprazole', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Cho uống lúc đói.', 0),
    ('sucralfate', 'Theo cân nặng', '3-4 lần/ngày', NULL, 'Cho uống lúc đói, cách các thuốc uống khác ít nhất 2 giờ.', 1),
    ('famotidine', 'Theo cân nặng', '1-2 lần/ngày', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Dị vật đường tiêu hóa - chó, mèo', 'di vat duong tieu hoa - cho, meo', 'Đau bụng do dị vật đường tiêu hóa: nôn liên tục, đau bụng, không đi ngoài.', 'Nội soi hoặc phẫu thuật lấy dị vật; truyền dịch và giảm đau.', 'Cấp cứu: không tự gây nôn, không cho ăn uống khi chưa có chỉ định. Đưa đi khám ngay.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('dich truyen', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('buprenorphine', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Có thể gây an thần.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Xoắn dạ dày (GDV) - chó', 'xoan da day (gdv) - cho', 'Xoắn dạ dày (GDV) ở chó: bụng chướng căng, nôn khan, bồn chồn.', 'Cấp cứu phẫu thuật; truyền dịch chống sốc và theo dõi nhịp tim.', 'Cấp cứu nguy hiểm tính mạng: đưa đi khám ngay, không chờ. Chó giống lớn, ngực sâu có nguy cơ cao.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('dich truyen', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('lidocaine', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Dùng khi có loạn nhịp tim theo chỉ định của bác sĩ.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm ruột cấp, tiêu chảy cấp - chó, mèo', 'viem ruot cap, tieu chay cap - cho, meo', 'Viêm ruột cấp, tiêu chảy cấp: tiêu chảy, đau quặn bụng, mất nước.', 'Bù nước, cầm tiêu chảy, bổ sung men vi sinh; cho ăn thức ăn dễ tiêu.', 'Tái khám nếu tiêu chảy ra máu, kéo dài quá 2-3 ngày hoặc thú lờ đờ, bỏ ăn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('metronidazole', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống cùng thức ăn.', 0),
    ('diosmectite (smecta)', 'Theo nhãn sản phẩm', 'Theo chỉ định của bác sĩ', NULL, 'Cho uống cách các thuốc khác ít nhất 2 giờ.', 1),
    ('men vi sinh (fortiflora)', 'Theo nhãn sản phẩm', '1 lần/ngày', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm ruột mạn (IBD) - chó, mèo', 'viem ruot man (ibd) - cho, meo', 'Viêm ruột mạn (IBD): nôn và tiêu chảy kéo dài, sụt cân.', 'Ức chế viêm bằng corticoid; kết hợp thức ăn thủy phân (VD: Hill''s z/d) theo chỉ định.', 'Bệnh mạn tính, cần theo dõi lâu dài. Không tự ngừng corticoid đột ngột.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('prednisolone', 'Theo cân nặng', '1-2 lần/ngày', NULL, 'Không tự ngừng đột ngột.', 0),
    ('budesonide', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Bác sĩ cân nhắc dùng thay hoặc phối hợp.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Trào ngược dạ dày thực quản - chó, mèo', 'trao nguoc da day thuc quan - cho, meo', 'Trào ngược dạ dày thực quản: ợ, nôn trớ, nuốt khó, ăn ít.', 'Giảm tiết acid và tăng nhu động dạ dày; chia nhỏ bữa ăn.', 'Tái khám nếu nôn trớ kéo dài hoặc thú sụt cân.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('omeprazole', 'Theo cân nặng', '1 lần/ngày', NULL, 'Cho uống lúc đói.', 0),
    ('metoclopramide', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Không dùng khi nghi tắc nghẽn.', 1),
    ('cisapride', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Không dùng khi nghi tắc nghẽn.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Táo bón, phình đại tràng - mèo', 'tao bon, phinh dai trang - meo', 'Táo bón, phình đại tràng ở mèo: rặn lâu, phân khô cứng, đau bụng.', 'Thuốc nhuận tràng, thuốc tăng nhu động ruột; thụt tháo khi cần.', 'Cho mèo uống đủ nước. Tái khám nếu không đi tiêu quá 48 giờ, nôn hoặc bỏ ăn.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('lactulose', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho thú uống đủ nước.', 0),
    ('cisapride', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Không dùng khi nghi tắc nghẽn.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Ngộ độc (socola, nho, chuột, thuốc người) - chó, mèo', 'ngo doc (socola, nho, chuot, thuoc nguoi) - cho, meo', 'Ngộ độc (socola, nho, thuốc chuột, thuốc người): nôn, run, co giật, đau bụng.', 'Cấp cứu: gây nôn khi còn kịp và phù hợp, than hoạt tính hấp phụ độc chất, truyền dịch hỗ trợ.', 'Cấp cứu: mang theo bao bì hoặc mẫu chất thú đã ăn. Không tự gây nôn tại nhà khi chưa hỏi bác sĩ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('apomorphine', 'Theo cân nặng', '1 lần', NULL, 'Chỉ dùng cho chó; bác sĩ quyết định có gây nôn hay không.', 0),
    ('than hoat tinh', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Không dùng khi thú lơ mơ hoặc không nuốt được.', 1),
    ('dich truyen', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Gan nhiễm mỡ - mèo', 'gan nhiem mo - meo', 'Gan nhiễm mỡ ở mèo: bỏ ăn nhiều ngày, vàng da, gầy.', 'Nuôi ăn bằng ống để cung cấp dinh dưỡng; kích thích ăn và hỗ trợ gan.', 'Mèo bỏ ăn quá 2-3 ngày cần đưa đi khám sớm. Tái khám xét nghiệm men gan.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('mirtazapine', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Dùng để kích thích ăn.', 0),
    ('same (s-adenosylmethionine)', 'Theo nhãn sản phẩm', '1 lần/ngày', NULL, 'Cho uống lúc đói.', 1),
    ('vitamin b12', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Sỏi bàng quang - chó', 'soi bang quang - cho', 'Sỏi bàng quang ở chó: tiểu ra máu, tiểu rắt.', 'Thức ăn tan sỏi (VD: Hill''s c/d, s/d) hoặc phẫu thuật lấy sỏi; kháng sinh khi có nhiễm trùng.', 'Cho chó uống nhiều nước. Tái khám siêu âm hoặc X-quang sau điều trị.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống sau ăn.', 0)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm da có mủ (Pyoderma) - chó', 'viem da co mu (pyoderma) - cho', 'Viêm da có mủ (Pyoderma) ở chó: mụn mủ, vảy, ngứa.', 'Kháng sinh đường uống kết hợp tắm bằng dầu tắm sát khuẩn.', 'Dùng đủ liệu trình kháng sinh. Tìm nguyên nhân nền (dị ứng, ký sinh trùng) để tránh tái phát.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('cephalexin', 'Theo cân nặng', '2 lần/ngày', NULL, NULL, 0),
    ('amoxicillin-clavulanate', 'Theo cân nặng', '2 lần/ngày', NULL, 'Bác sĩ cân nhắc dùng thay cho Cephalexin.', 1),
    ('chlorhexidine', 'Theo nhãn sản phẩm', '2-3 lần/tuần', NULL, 'Dầu tắm; tránh vùng mắt.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Viêm loét giác mạc - chó', 'viem loet giac mac - cho', 'Viêm loét giác mạc ở chó: nheo mắt, chảy nước mắt, mắt đục.', 'Nhỏ kháng sinh, nhỏ thuốc giãn đồng tử giảm đau; đeo vòng cổ chống liếm, không cho cào mắt.', 'Bệnh có thể nặng lên rất nhanh: tái khám đúng hẹn để kiểm tra vết loét.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('tobramycin (nho mat)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Dùng thay hoặc luân phiên với Ofloxacin theo chỉ định.', 0),
    ('ofloxacin (nho mat)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1),
    ('atropine (nho mat)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Không dùng khi tăng nhãn áp.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Động kinh - chó', 'dong kinh - cho', 'Động kinh ở chó: co giật từng cơn, sùi bọt mép.', 'Thuốc chống co giật dùng đều đặn lâu dài; ghi lại thời gian và tần suất mỗi cơn.', 'Không tự ngừng thuốc đột ngột. Cơn co giật kéo dài quá 5 phút là cấp cứu. Xét nghiệm nồng độ thuốc và men gan định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('phenobarbital', 'Theo cân nặng', '2 lần/ngày', NULL, 'Cho uống đúng giờ mỗi ngày.', 0),
    ('levetiracetam', 'Theo cân nặng', '2-3 lần/ngày', NULL, NULL, 1),
    ('kali bromide', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Bác sĩ chỉ định khi cần phối hợp thêm thuốc.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Thoát vị đĩa đệm - chó', 'thoat vi dia dem - cho', 'Thoát vị đĩa đệm ở chó (Corgi, Dachshund...): đau lưng, run, yếu hoặc liệt chân sau.', 'Nghỉ ngơi trong chuồng hạn chế vận động; giảm đau, kháng viêm, giãn cơ; phẫu thuật nếu liệt nặng.', 'Không dùng chung Prednisolone và Meloxicam. Chân sau yếu hoặc liệt cần khám ngay.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('gabapentin', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Có thể gây buồn ngủ.', 0),
    ('prednisolone', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng thay cho Meloxicam, không dùng chung.', 1),
    ('meloxicam', 'Theo cân nặng', '1 lần/ngày', NULL, 'Dùng thay cho Prednisolone, không dùng chung.', 2),
    ('methocarbamol', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, NULL, 3)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Bệnh cơ tim phì đại (HCM) - mèo', 'benh co tim phi dai (hcm) - meo', 'Bệnh cơ tim phì đại (HCM) ở mèo: thở nhanh, liệt chân sau đột ngột (do huyết khối).', 'Chống đông tiểu cầu phòng huyết khối, kiểm soát nhịp tim, lợi tiểu khi có dịch phổi.', 'Liệt chân sau đột ngột hoặc thở há miệng là cấp cứu. Siêu âm tim định kỳ.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('clopidogrel', 'Theo cân nặng', '1 lần/ngày', NULL, NULL, 0),
    ('atenolol', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Không tự ngừng đột ngột.', 1),
    ('furosemide', 'Theo cân nặng', 'Theo chỉ định của bác sĩ', NULL, 'Để mèo uống nước tự do.', 2)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Mụn cằm - mèo', 'mun cam - meo', 'Mụn cằm ở mèo: mụn đen hoặc mụn mủ ở cằm.', 'Vệ sinh sát khuẩn vùng cằm, bôi thuốc kháng khuẩn; đổi bát ăn sang inox hoặc sứ.', 'Rửa bát ăn thường xuyên. Tái khám nếu mụn mủ lan rộng hoặc sưng.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('chlorhexidine', 'Theo nhãn sản phẩm', '1-2 lần/ngày', NULL, 'Lau vùng cằm, tránh vùng mắt.', 0),
    ('mupirocin', 'Theo nhãn sản phẩm', '2 lần/ngày', NULL, 'Bôi mỏng, không để mèo liếm.', 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Gout - chim cảnh', 'gout - chim canh', 'Gout ở chim cảnh: khớp chân sưng, có nốt trắng, đứng không vững.', 'Giảm acid uric, bù nước; giảm đạm trong khẩu phần.', 'Điều chỉnh khẩu phần ăn theo hướng dẫn của bác sĩ. Tái khám xét nghiệm acid uric.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('allopurinol', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('dich truyen', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Thiếu canxi - chim cảnh', 'thieu canxi - chim canh', 'Thiếu canxi ở chim cảnh: run, co giật, trứng vỏ mềm.', 'Bổ sung canxi và vitamin D3; cho chim tắm nắng hoặc dùng đèn UVB.', 'Thừa vitamin D3 cũng gây ngộ độc: dùng đúng liều bác sĩ chỉ định.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('calcium gluconate', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, 'Tiêm theo chỉ định của bác sĩ.', 0),
    ('vitamin d3', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;

WITH t AS (
  INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by)
  VALUES ('Ngộ độc kim loại nặng (chì, kẽm) - chim cảnh', 'ngo doc kim loai nang (chi, kem) - chim canh', 'Ngộ độc kim loại nặng (chì, kẽm) ở chim cảnh: nôn, phân lẫn máu, co giật.', 'Thuốc thải kim loại nặng; điều trị hỗ trợ; tìm và loại bỏ nguồn kim loại.', 'Kiểm tra lồng, đồ chơi, dây kim loại mạ kẽm. Theo dõi chức năng gan, thận.', '00000000-0000-0000-0000-000000000000'::uuid)
  ON CONFLICT (search_name) DO NOTHING
  RETURNING id
)
INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, v.days::int, v.notes, v.ord
FROM t
CROSS JOIN (VALUES
    ('caedta', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 0),
    ('dmsa (succimer)', 'Theo chỉ định của bác sĩ', 'Theo chỉ định của bác sĩ', NULL, NULL, 1)
) AS v(med, dosage, frequency, days, notes, ord)
JOIN medications m ON m.search_name = v.med;
