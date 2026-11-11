-- DỮ LIỆU THỬ cho danh mục thuốc (pet-service, bảng medications).
--
-- Lập từ bảng "bệnh thường gặp ở chó, mèo, chim cảnh" mà phòng khám cung cấp, cộng với các lưu ý
-- phổ biến. KHÔNG phải tài liệu chuyên môn: bác sĩ cần rà lại tên thuốc, đơn vị và lưu ý trước khi
-- dùng thật. Không có liều lượng ở đây vì liều phụ thuộc từng ca.
--
-- Không đặt trong db/migration: migration chạy cả ở môi trường thật và test, còn đây chỉ là dữ liệu mẫu.
-- Chạy lại được nhiều lần (thuốc đã có theo search_name thì bỏ qua):
--   PGPASSWORD=... psql -h 127.0.0.1 -p 5440 -U postgres -d pet_db -f scripts/seed-medications.sql
--
-- search_name = tên bỏ dấu, chữ thường, gộp khoảng trắng — khớp TextNormalizer của backend.
INSERT INTO medications (name, search_name, unit, caution) VALUES
  ('Lactated Ringer', 'lactated ringer', 'chai', 'Dịch truyền tĩnh mạch. Thận trọng ở thú suy tim hoặc suy thận: truyền quá nhanh hay quá nhiều có thể gây quá tải dịch.'),
  ('Maropitant (Cerenia)', 'maropitant (cerenia)', 'viên', 'Thận trọng ở thú non và thú bệnh gan. Kiểm tra độ tuổi tối thiểu theo nhãn sản phẩm.'),
  ('Metronidazole', 'metronidazole', 'viên', 'Liều cao hoặc dùng kéo dài có thể gây độc thần kinh (loạng choạng, run). Thận trọng ở thú bệnh gan và thú mang thai.'),
  ('Ampicillin', 'ampicillin', 'lọ', 'Không dùng cho thú có tiền sử dị ứng nhóm penicillin. Thận trọng ở thỏ, chuột lang, hamster vì kháng sinh nhóm này có thể gây rối loạn hệ vi khuẩn ruột.'),
  ('Amoxicillin-Clavulanate', 'amoxicillin-clavulanate', 'viên', 'Không dùng cho thú có tiền sử dị ứng nhóm penicillin. Thận trọng ở thỏ, chuột lang, hamster vì có thể gây rối loạn hệ vi khuẩn ruột.'),
  ('Doxycycline', 'doxycycline', 'viên', 'Không dùng cho thú mang thai và thú non đang mọc răng. Ở mèo nên cho uống kèm nước hoặc thức ăn vì viên có thể gây viêm, hẹp thực quản.'),
  ('Phenobarbital', 'phenobarbital', 'viên', 'Có thể gây buồn ngủ, loạng choạng lúc đầu. Thận trọng ở thú bệnh gan. Không ngừng đột ngột vì có thể gây co giật bùng phát.'),
  ('Butorphanol', 'butorphanol', 'ml', 'Gây an thần. Thận trọng ở thú bệnh gan, bệnh thận, suy hô hấp và chấn thương đầu.'),
  ('Fluralaner (Bravecto)', 'fluralaner (bravecto)', 'viên', 'Thận trọng ở thú có tiền sử động kinh hoặc co giật. Kiểm tra tuổi và cân nặng tối thiểu theo nhãn sản phẩm.'),
  ('Afoxolaner (NexGard)', 'afoxolaner (nexgard)', 'viên', 'Thận trọng ở thú có tiền sử động kinh hoặc co giật. Kiểm tra tuổi và cân nặng tối thiểu theo nhãn sản phẩm.'),
  ('Ivermectin', 'ivermectin', 'ml', 'Không dùng cho chó giống Collie và chó chăn cừu: dễ nhạy cảm với thuốc (đột biến gen MDR1), có thể gây độc thần kinh.'),
  ('Melarsomine (Immiticide)', 'melarsomine (immiticide)', 'lọ', 'Chỉ dùng cho chó, không dùng cho mèo. Sau điều trị phải cho nghỉ ngơi nghiêm ngặt vì nguy cơ tắc mạch phổi do giun chết.'),
  ('Prednisolone', 'prednisolone', 'viên', 'Không dùng khi đang nhiễm nấm hoặc loét dạ dày. Thận trọng ở thú tiểu đường, thú mang thai. Dùng kéo dài thì không ngừng đột ngột.'),
  ('Surolan', 'surolan', 'chai', 'Thuốc nhỏ tai. Không dùng khi màng nhĩ thủng. Thận trọng ở thú mang thai.'),
  ('Otomax', 'otomax', 'tuýp', 'Thuốc nhỏ tai. Không dùng khi màng nhĩ thủng (nguy cơ độc với tai). Thận trọng ở thú mang thai.'),
  ('Epi-Otic', 'epi-otic', 'chai', 'Dung dịch vệ sinh tai. Thận trọng khi màng nhĩ có thể đã thủng.'),
  ('Imidocarb', 'imidocarb', 'ml', 'Có thể gây chảy nước dãi, nôn, tiêu chảy sau tiêm. Thận trọng ở thú bệnh gan, bệnh thận và thú đang dùng thuốc ức chế cholinesterase.'),
  ('Diminazene', 'diminazene', 'lọ', 'Khoảng liều an toàn hẹp: không vượt liều. Có thể gây độc thần kinh và tổn thương gan, nhất là ở chó.'),
  ('Milbemycin', 'milbemycin', 'viên', 'Giống Collie có thể nhạy cảm khi dùng liều cao hơn liều phòng giun tim thông thường.'),
  ('Interferon omega (Virbagen Omega)', 'interferon omega (virbagen omega)', 'lọ', NULL),
  ('Famciclovir', 'famciclovir', 'viên', 'Dùng cho mèo nhiễm Herpesvirus. Cần chỉnh liều ở mèo suy thận.'),
  ('Oxytetracycline', 'oxytetracycline', 'tuýp', NULL),
  ('GS-441524', 'gs-441524', 'ml', 'Tình trạng pháp lý và nguồn thuốc khác nhau tùy nơi. Kiểm tra trước khi kê.'),
  ('Remdesivir', 'remdesivir', 'lọ', 'Tình trạng pháp lý và nguồn thuốc khác nhau tùy nơi. Kiểm tra trước khi kê.'),
  ('Molnupiravir', 'molnupiravir', 'viên', 'Tình trạng pháp lý và nguồn thuốc khác nhau tùy nơi. Kiểm tra trước khi kê.'),
  ('Zidovudine (AZT)', 'zidovudine (azt)', 'viên', 'Có thể gây thiếu máu. Cần xét nghiệm máu định kỳ khi dùng.'),
  ('Buprenorphine', 'buprenorphine', 'ml', 'Thuốc giảm đau nhóm opioid, có thể gây an thần. Thận trọng ở thú bệnh gan hoặc suy hô hấp.'),
  ('Prazosin', 'prazosin', 'viên', 'Có thể gây hạ huyết áp. Thận trọng ở thú suy thận hoặc đang dùng thuốc hạ áp. Mèo đực tắc tiểu là ca cấp cứu: thuốc không thay thế việc thông tiểu.'),
  ('Itraconazole', 'itraconazole', 'viên', 'Dễ gây độc cho vẹt xám châu Phi. Có thể gây độc gan: theo dõi men gan khi dùng kéo dài.'),
  ('Terbinafine', 'terbinafine', 'viên', 'Theo dõi men gan khi dùng kéo dài.'),
  ('Lime sulfur', 'lime sulfur', 'chai', 'Dùng ngoài da. Phải pha đúng nồng độ, có mùi nặng và làm ố đồ vật. Ngăn thú liếm vì nuốt phải có thể gây nôn. Nấm có thể lây sang người: cần vệ sinh môi trường.'),
  ('Miconazole', 'miconazole', 'chai', 'Dầu tắm dùng ngoài da. Tránh vùng mắt và không để thú liếm. Nấm có thể lây sang người: cần vệ sinh môi trường.'),
  ('Clindamycin', 'clindamycin', 'viên', 'Không dùng cho thỏ, chuột lang, hamster (nguy cơ viêm ruột nặng). Có thể gây nôn, tiêu chảy.'),
  ('Azithromycin', 'azithromycin', 'viên', 'Thận trọng ở thú bệnh gan. Có thể gây nôn hoặc tiêu chảy.'),
  ('Voriconazole', 'voriconazole', 'viên', 'Ở mèo, liều cao có thể gây độc thần kinh và ảnh hưởng thị lực. Thận trọng ở thú bệnh gan, theo dõi men gan.'),
  ('Amphotericin B', 'amphotericin b', 'lọ', 'Đường tiêm độc với thận. Dạng khí dung dùng theo chỉ định của bác sĩ.'),
  ('Toltrazuril (Baycox)', 'toltrazuril (baycox)', 'chai', NULL),
  ('Fenbendazole', 'fenbendazole', 'gói', 'Thận trọng ở chim đang thay lông và một số loài nhạy cảm (cò, diệc, bồ câu).')
ON CONFLICT (search_name) DO NOTHING;
