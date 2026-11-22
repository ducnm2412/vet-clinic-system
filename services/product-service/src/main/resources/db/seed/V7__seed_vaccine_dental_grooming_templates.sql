-- Thuốc, vắc xin và đơn mẫu cho 3 dịch vụ: Tiêm phòng – tẩy giun, Chăm sóc răng miệng, Tắm sấy – spa.
-- Tên mẫu có sẵn từ khoá "vắc xin", "tẩy giun", "răng", "tắm" (viết có dấu cách, không gạch nối) để
-- gõ từ đó vào ô tìm đơn mẫu là ra (tìm theo tên mẫu, bỏ dấu, không phân biệt hoa thường).
-- Thuốc mới đặt giá và tồn đầu TẠM (100): nhân viên chỉnh lại trong danh mục thuốc.
-- Liều/lịch tiêm theo tài liệu thú y thông dụng, bác sĩ phòng khám cần rà lại trước khi dùng thật.
-- Thuốc dùng lại (Pyrantel, Praziquantel, Selamectin, Epi-Otic) lấy từ danh mục seed V5.

INSERT INTO medications (name, search_name, unit, caution, active, unit_price, stock_quantity, low_stock_threshold) VALUES
    ('Vắc xin 5 bệnh chó (DHPPi)', 'vac xin 5 benh cho (dhppi)', 'liều', 'Chỉ tiêm cho thú khoẻ, không sốt, đã tẩy giun. Theo dõi phản ứng 15-30 phút sau tiêm.', true, 150000, 100, 10),
    ('Vắc xin 7 bệnh chó (DHPPi + Lepto)', 'vac xin 7 benh cho (dhppi + lepto)', 'liều', 'Chỉ tiêm cho thú khoẻ, không sốt, đã tẩy giun. Theo dõi phản ứng 15-30 phút sau tiêm.', true, 220000, 100, 10),
    ('Vắc xin dại (Rabies)', 'vac xin dai (rabies)', 'liều', 'Chỉ tiêm cho thú khoẻ, không sốt. Theo dõi phản ứng 15-30 phút sau tiêm.', true, 120000, 100, 10),
    ('Vắc xin 3 bệnh mèo (FVRCP)', 'vac xin 3 benh meo (fvrcp)', 'liều', 'Chỉ tiêm cho mèo khoẻ, không sốt, đã tẩy giun. Theo dõi phản ứng 15-30 phút sau tiêm.', true, 200000, 100, 10),
    ('Vắc xin 4 bệnh mèo (FVRCP + Chlamydia)', 'vac xin 4 benh meo (fvrcp + chlamydia)', 'liều', 'Chỉ tiêm cho mèo khoẻ, không sốt, đã tẩy giun. Theo dõi phản ứng 15-30 phút sau tiêm.', true, 250000, 100, 10),
    ('Stomorgyl (spiramycin + metronidazole)', 'stomorgyl (spiramycin + metronidazole)', 'viên', 'Thận trọng ở thú bệnh gan. Không dùng cho thú có tiền sử dị ứng thuốc.', true, 15000, 100, 10),
    ('Gel vệ sinh răng miệng (Chlorhexidine)', 'gel ve sinh rang mieng (chlorhexidine)', 'tuýp', 'Dùng ngoài, không nuốt hết. Dùng kéo dài có thể làm ố răng nhẹ.', true, 90000, 100, 10),
    ('Dầu tắm trị ve bọ chét', 'dau tam tri ve bo chet', 'chai', 'Dùng ngoài da. Tránh vùng mắt, tai. Thận trọng với mèo con và thú đang mang thai.', true, 180000, 100, 10),
    ('Dầu tắm dưỡng da, lông', 'dau tam duong da, long', 'chai', 'Dùng ngoài da. Tránh vùng mắt, tai.', true, 150000, 100, 10),
    ('Bột cầm máu cắt móng', 'bot cam mau cat mong', 'lọ', 'Chỉ dùng ngoài khi lỡ cắt móng chảy máu.', true, 60000, 100, 10);

-- Tồn đầu của thuốc mới cũng ghi vết như các thuốc đã có (type IMPORT).
INSERT INTO medication_stock_movements (medication_id, type, quantity_change, quantity_after, note)
SELECT id, 'IMPORT', 100, 100, 'Tồn đầu (seed)'
FROM medications WHERE search_name IN ('vac xin 5 benh cho (dhppi)', 'vac xin 7 benh cho (dhppi + lepto)', 'vac xin dai (rabies)', 'vac xin 3 benh meo (fvrcp)', 'vac xin 4 benh meo (fvrcp + chlamydia)', 'stomorgyl (spiramycin + metronidazole)', 'gel ve sinh rang mieng (chlorhexidine)', 'dau tam tri ve bo chet', 'dau tam duong da, long', 'bot cam mau cat mong');

INSERT INTO prescription_templates (name, search_name, diagnosis, treatment, notes, created_by) VALUES
    ('Tiêm phòng vắc xin 5 bệnh - chó', 'tiem phong vac xin 5 benh - cho', 'Tiêm phòng 5 bệnh cho chó: Care (sài sốt), Parvo, viêm gan truyền nhiễm, ho cũi chó, á cúm.', 'Tiêm vắc xin 5 bệnh dưới da. Chó con tiêm mũi đầu lúc 6-8 tuần tuổi, nhắc lại mỗi 3-4 tuần cho đủ liều, sau đó nhắc hằng năm.', 'Chỉ tiêm khi thú khoẻ, không sốt, đã tẩy giun. Ở lại theo dõi 15-30 phút sau tiêm. Hẹn lịch nhắc mũi sau.', '00000000-0000-0000-0000-000000000000'),
    ('Tiêm phòng vắc xin 7 bệnh - chó', 'tiem phong vac xin 7 benh - cho', 'Tiêm phòng 7 bệnh cho chó: Care (sài sốt), Parvo, viêm gan truyền nhiễm, ho cũi chó, á cúm, Leptospirosis.', 'Tiêm vắc xin 7 bệnh dưới da. Chó con tiêm mũi đầu lúc 6-8 tuần tuổi, nhắc lại mỗi 3-4 tuần cho đủ liều, sau đó nhắc hằng năm.', 'Chỉ tiêm khi thú khoẻ, không sốt, đã tẩy giun. Ở lại theo dõi 15-30 phút sau tiêm. Hẹn lịch nhắc mũi sau.', '00000000-0000-0000-0000-000000000000'),
    ('Tiêm phòng vắc xin dại - chó, mèo', 'tiem phong vac xin dai - cho, meo', 'Tiêm phòng bệnh dại cho chó, mèo.', 'Tiêm vắc xin dại dưới da từ 3 tháng tuổi, nhắc lại hằng năm.', 'Chỉ tiêm khi thú khoẻ, không sốt. Ở lại theo dõi 15-30 phút sau tiêm. Cấp sổ tiêm phòng cho chủ nuôi.', '00000000-0000-0000-0000-000000000000'),
    ('Tiêm phòng vắc xin 3 bệnh - mèo', 'tiem phong vac xin 3 benh - meo', 'Tiêm phòng 3 bệnh cho mèo: viêm mũi khí quản, Calicivirus, giảm bạch cầu.', 'Tiêm vắc xin 3 bệnh dưới da. Mèo con tiêm mũi đầu lúc 8-9 tuần tuổi, nhắc lại mỗi 3-4 tuần cho đủ liều, sau đó nhắc hằng năm.', 'Chỉ tiêm khi mèo khoẻ, không sốt, đã tẩy giun. Ở lại theo dõi 15-30 phút sau tiêm. Hẹn lịch nhắc mũi sau.', '00000000-0000-0000-0000-000000000000'),
    ('Tiêm phòng vắc xin 4 bệnh - mèo', 'tiem phong vac xin 4 benh - meo', 'Tiêm phòng 4 bệnh cho mèo: viêm mũi khí quản, Calicivirus, giảm bạch cầu, Chlamydia.', 'Tiêm vắc xin 4 bệnh dưới da. Mèo con tiêm mũi đầu lúc 8-9 tuần tuổi, nhắc lại mỗi 3-4 tuần cho đủ liều, sau đó nhắc hằng năm.', 'Chỉ tiêm khi mèo khoẻ, không sốt, đã tẩy giun. Ở lại theo dõi 15-30 phút sau tiêm. Hẹn lịch nhắc mũi sau.', '00000000-0000-0000-0000-000000000000'),
    ('Tẩy giun định kỳ - chó, mèo', 'tay giun dinh ky - cho, meo', 'Tẩy giun sán định kỳ cho chó, mèo khoẻ mạnh.', 'Uống thuốc tẩy giun sán một lần theo cân nặng. Thú con tẩy lại sau 2-3 tuần; thú trưởng thành tẩy 3-6 tháng một lần.', 'Cân thú trước khi tính liều. Không tẩy giun cho thú đang ốm nặng.', '00000000-0000-0000-0000-000000000000'),
    ('Cạo vôi răng - chó, mèo', 'cao voi rang - cho, meo', 'Cao răng, viêm nướu nhẹ.', 'Cạo vôi, đánh bóng răng; hướng dẫn vệ sinh răng miệng tại nhà.', 'Thường cần gây mê: nhịn ăn theo hướng dẫn của bác sĩ. Thuốc gây mê dùng tại phòng khám, không ghi trong đơn. Cho ăn mềm vài ngày sau cạo vôi.', '00000000-0000-0000-0000-000000000000'),
    ('Viêm nướu, viêm răng miệng sau cạo vôi - chó, mèo', 'viem nuou, viem rang mieng sau cao voi - cho, meo', 'Viêm nướu, viêm răng miệng; dùng kháng sinh sau cạo vôi.', 'Kháng sinh đường uống, vệ sinh răng miệng bằng gel. Cho ăn mềm đến khi nướu lành.', 'Cần giảm đau thì bác sĩ thêm theo từng con (mèo thận trọng với thuốc kháng viêm). Tái khám sau 7-10 ngày.', '00000000-0000-0000-0000-000000000000'),
    ('Tắm trị ve bọ chét - chó, mèo', 'tam tri ve bo chet - cho, meo', 'Nhiễm ve, bọ chét ngoài da.', 'Tắm bằng dầu tắm trị ve bọ chét, sau đó nhỏ thuốc phòng ký sinh trùng lên da.', 'Vệ sinh nơi ở của thú. Nhắc lại thuốc nhỏ gáy sau 1 tháng.', '00000000-0000-0000-0000-000000000000'),
    ('Tắm dưỡng da, lông - chó, mèo', 'tam duong da, long - cho, meo', 'Tắm vệ sinh, dưỡng da và lông.', 'Tắm, sấy bằng dầu tắm dưỡng da lông.', 'Có thể kèm cắt tỉa lông, vệ sinh tai móng theo yêu cầu.', '00000000-0000-0000-0000-000000000000'),
    ('Tắm, vệ sinh tai và cắt móng - chó, mèo', 'tam, ve sinh tai va cat mong - cho, meo', 'Vệ sinh tai, cắt móng khi tắm spa.', 'Làm sạch ống tai bằng dung dịch vệ sinh tai, cắt móng.', 'Nếu tai có mùi, tiết dịch nhiều hoặc thú gãi tai liên tục: nghi viêm tai, cần khám thay vì chỉ vệ sinh.', '00000000-0000-0000-0000-000000000000');

INSERT INTO prescription_template_items (template_id, medication_id, dosage, frequency, duration_days, notes, sort_order)
SELECT t.id, m.id, v.dosage, v.frequency, CAST(v.days AS INTEGER), v.notes, CAST(v.sort_order AS INTEGER)
FROM (VALUES
    ('tiem phong vac xin 5 benh - cho', 'vac xin 5 benh cho (dhppi)', '1 liều', '1 lần (tiêm dưới da)', NULL, NULL, 0),
    ('tiem phong vac xin 5 benh - cho', 'pyrantel', 'Theo cân nặng', '1 lần', NULL, 'Nên tẩy giun trước khi tiêm 7-10 ngày. Bỏ dòng này nếu thú đã được tẩy giun gần đây.', 1),
    ('tiem phong vac xin 7 benh - cho', 'vac xin 7 benh cho (dhppi + lepto)', '1 liều', '1 lần (tiêm dưới da)', NULL, NULL, 0),
    ('tiem phong vac xin 7 benh - cho', 'pyrantel', 'Theo cân nặng', '1 lần', NULL, 'Nên tẩy giun trước khi tiêm 7-10 ngày. Bỏ dòng này nếu thú đã được tẩy giun gần đây.', 1),
    ('tiem phong vac xin dai - cho, meo', 'vac xin dai (rabies)', '1 liều', '1 lần (tiêm dưới da)', NULL, NULL, 0),
    ('tiem phong vac xin 3 benh - meo', 'vac xin 3 benh meo (fvrcp)', '1 liều', '1 lần (tiêm dưới da)', NULL, NULL, 0),
    ('tiem phong vac xin 3 benh - meo', 'pyrantel', 'Theo cân nặng', '1 lần', NULL, 'Nên tẩy giun trước khi tiêm 7-10 ngày. Bỏ dòng này nếu thú đã được tẩy giun gần đây.', 1),
    ('tiem phong vac xin 4 benh - meo', 'vac xin 4 benh meo (fvrcp + chlamydia)', '1 liều', '1 lần (tiêm dưới da)', NULL, NULL, 0),
    ('tiem phong vac xin 4 benh - meo', 'pyrantel', 'Theo cân nặng', '1 lần', NULL, 'Nên tẩy giun trước khi tiêm 7-10 ngày. Bỏ dòng này nếu thú đã được tẩy giun gần đây.', 1),
    ('tay giun dinh ky - cho, meo', 'pyrantel', 'Theo cân nặng', '1 lần', NULL, NULL, 0),
    ('tay giun dinh ky - cho, meo', 'praziquantel', 'Theo cân nặng', '1 lần', NULL, 'Kiểm tra tuổi tối thiểu theo nhãn.', 1),
    ('cao voi rang - cho, meo', 'gel ve sinh rang mieng (chlorhexidine)', 'Bôi hoặc chải lên răng, nướu', '1 lần/ngày', 7, NULL, 0),
    ('viem nuou, viem rang mieng sau cao voi - cho, meo', 'stomorgyl (spiramycin + metronidazole)', 'Theo cân nặng', '1 lần/ngày', 5, NULL, 0),
    ('viem nuou, viem rang mieng sau cao voi - cho, meo', 'gel ve sinh rang mieng (chlorhexidine)', 'Bôi hoặc chải lên răng, nướu', '1 lần/ngày', 7, NULL, 1),
    ('tam tri ve bo chet - cho, meo', 'dau tam tri ve bo chet', '1 lần tắm', '1 lần', NULL, 'Để dầu tắm trên lông vài phút theo hướng dẫn rồi xả sạch.', 0),
    ('tam tri ve bo chet - cho, meo', 'selamectin (revolution)', 'Theo cân nặng', '1 lần', NULL, 'Nhỏ lên da khô vùng gáy; không tắm lại sớm sau khi nhỏ.', 1),
    ('tam duong da, long - cho, meo', 'dau tam duong da, long', '1 lần tắm', '1 lần', NULL, NULL, 0),
    ('tam, ve sinh tai va cat mong - cho, meo', 'epi-otic', 'Theo hướng dẫn trên nhãn', '1 lần', NULL, NULL, 0),
    ('tam, ve sinh tai va cat mong - cho, meo', 'bot cam mau cat mong', 'Khi cần', 'Khi cần', NULL, 'Chỉ dùng khi lỡ cắt móng chảy máu. Bỏ dòng này nếu không dùng.', 1)
) AS v(tpl, med, dosage, frequency, days, notes, sort_order)
JOIN prescription_templates t ON t.search_name = v.tpl
JOIN medications m ON m.search_name = v.med;

-- Chặn migration nếu thiếu thuốc nào (JOIN lặng lẽ bỏ dòng): số dòng thuốc phải đủ.
DO $$
BEGIN
    IF (SELECT count(*) FROM prescription_template_items i JOIN prescription_templates t ON t.id = i.template_id
        WHERE t.search_name IN ('tiem phong vac xin 5 benh - cho', 'tiem phong vac xin 7 benh - cho', 'tiem phong vac xin dai - cho, meo', 'tiem phong vac xin 3 benh - meo', 'tiem phong vac xin 4 benh - meo', 'tay giun dinh ky - cho, meo', 'cao voi rang - cho, meo', 'viem nuou, viem rang mieng sau cao voi - cho, meo', 'tam tri ve bo chet - cho, meo', 'tam duong da, long - cho, meo', 'tam, ve sinh tai va cat mong - cho, meo')) <> 19 THEN
        RAISE EXCEPTION 'Seed don mau: thieu thuoc trong danh muc, so dong thuoc khong du 19';
    END IF;
END $$;
