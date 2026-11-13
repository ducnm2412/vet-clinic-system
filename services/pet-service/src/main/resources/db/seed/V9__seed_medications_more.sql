-- Bo sung danh muc thuoc (pet-service, bang medications) cho nhom benh 51-70 trong bang benh
-- phong kham cung cap: tieu hoa, than kinh, co xuong khop, tim mach, mat, da, chim canh.
--
-- KHONG phai tai lieu chuyen mon: bac si can ra lai ten thuoc, don vi va luu y truoc khi dung that.
-- Khong co lieu luong o day vi lieu phu thuoc tung ca.
--
-- ON CONFLICT DO NOTHING theo search_name nhu V7: thuoc da co thi khong ghi de.
INSERT INTO medications (name, search_name, unit, caution) VALUES
  ('Omeprazole', 'omeprazole', 'viên', 'Cho uống lúc đói, trước bữa ăn khoảng 30 phút.'),
  ('Famotidine', 'famotidine', 'viên', 'Thận trọng ở thú suy thận: có thể cần giảm liều.'),
  ('Sucralfate', 'sucralfate', 'ml', 'Cho uống lúc đói. Cho uống cách các thuốc uống khác ít nhất 2 giờ vì làm giảm hấp thu thuốc.'),
  ('Lidocaine', 'lidocaine', 'ống', 'Thận trọng loạn nhịp tim. Mèo rất nhạy cảm với lidocaine: chỉ dùng theo chỉ định của bác sĩ.'),
  ('Diosmectite (Smecta)', 'diosmectite (smecta)', 'gói', 'Cho uống cách các thuốc khác ít nhất 2 giờ vì có thể làm giảm hấp thu thuốc.'),
  ('Men vi sinh (FortiFlora)', 'men vi sinh (fortiflora)', 'gói', NULL),
  ('Budesonide', 'budesonide', 'viên', 'Là corticoid: không tự ngừng đột ngột. Thận trọng khi dùng chung với thuốc kháng viêm NSAID.'),
  ('Metoclopramide', 'metoclopramide', 'viên', 'Không dùng khi nghi tắc nghẽn đường tiêu hoá. Có thể gây bồn chồn, kích động.'),
  ('Cisapride', 'cisapride', 'viên', 'Không dùng khi nghi tắc nghẽn đường tiêu hoá.'),
  ('Lactulose', 'lactulose', 'ml', 'Dùng quá liều gây tiêu chảy, đầy hơi. Cho thú uống đủ nước.'),
  ('Apomorphine', 'apomorphine', 'ống', 'Chỉ dùng gây nôn cho chó, không dùng cho mèo. Không gây nôn khi thú lơ mơ, co giật hoặc đã nuốt chất ăn mòn (axit, kiềm), dầu hoả. Chỉ dùng theo chỉ định của bác sĩ.'),
  ('Than hoạt tính', 'than hoat tinh', 'gói', 'Không dùng khi thú lơ mơ hoặc không nuốt được vì nguy cơ sặc vào phổi. Có thể gây táo bón, mất nước.'),
  ('Mirtazapine', 'mirtazapine', 'viên', 'Dùng để kích thích ăn. Thận trọng ở thú bệnh gan, bệnh thận: có thể cần giảm liều.'),
  ('Vitamin B12', 'vitamin b12', 'ml', NULL),
  ('Cephalexin', 'cephalexin', 'viên', 'Thận trọng ở thú có tiền sử dị ứng kháng sinh nhóm cephalosporin hoặc penicillin. Nếu thú nôn khi uống thuốc lúc đói, cho uống cùng thức ăn.'),
  ('Chlorhexidine', 'chlorhexidine', 'chai', 'Dùng ngoài da. Tránh vùng mắt, không để thú liếm hoặc nuốt.'),
  ('Tobramycin (nhỏ mắt)', 'tobramycin (nho mat)', 'lọ', 'Dùng nhỏ mắt. Không để đầu lọ chạm vào mắt.'),
  ('Ofloxacin (nhỏ mắt)', 'ofloxacin (nho mat)', 'lọ', 'Dùng nhỏ mắt. Không để đầu lọ chạm vào mắt.'),
  ('Atropine (nhỏ mắt)', 'atropine (nho mat)', 'lọ', 'Làm giãn đồng tử, thú có thể sợ ánh sáng vài ngày. Không dùng khi thú bị tăng nhãn áp (glaucoma). Thuốc chảy xuống họng có thể làm chó chảy nhiều nước dãi.'),
  ('Levetiracetam', 'levetiracetam', 'viên', 'Không tự ngừng thuốc đột ngột vì có thể gây co giật trở lại. Cần giảm liều ở thú suy thận.'),
  ('Kali bromide', 'kali bromide', 'ml', 'Không tự ngừng thuốc đột ngột. Không dùng cho mèo vì có thể gây bệnh đường hô hấp. Giữ lượng muối trong khẩu phần ổn định.'),
  ('Methocarbamol', 'methocarbamol', 'viên', 'Có thể gây buồn ngủ, loạng choạng.'),
  ('Clopidogrel', 'clopidogrel', 'viên', 'Thuốc chống kết tập tiểu cầu: tăng nguy cơ chảy máu. Báo bác sĩ trước khi gây mê, phẫu thuật.'),
  ('Atenolol', 'atenolol', 'viên', 'Thận trọng ở thú suy tim sung huyết hoặc suy thận. Không tự ngừng thuốc đột ngột.'),
  ('Mupirocin', 'mupirocin', 'tuýp', 'Dùng ngoài da. Không để thú liếm.'),
  ('Allopurinol', 'allopurinol', 'viên', 'Thận trọng ở thú suy thận.'),
  ('Vitamin D3', 'vitamin d3', 'ml', 'Thừa vitamin D3 gây ngộ độc (tăng canxi máu): chỉ dùng đúng liều bác sĩ chỉ định.'),
  ('CaEDTA', 'caedta', 'ống', 'Phải dùng đúng loại calcium EDTA (CaEDTA), không dùng muối natri EDTA. Có thể gây độc thận: theo dõi chức năng thận.'),
  ('DMSA (Succimer)', 'dmsa (succimer)', 'viên', 'Thuốc thải kim loại nặng dạng uống. Theo dõi chức năng gan, thận khi dùng.')
ON CONFLICT (search_name) DO NOTHING;
