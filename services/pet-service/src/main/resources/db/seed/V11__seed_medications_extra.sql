-- Bo sung danh muc thuoc (pet-service, bang medications) cho nhom benh 71-120 trong bang benh
-- phong kham cung cap: mat, ung thu, mien dich, noi tiet, tim mach, hanh vi, chim canh.
--
-- KHONG phai tai lieu chuyen mon: bac si can ra lai ten thuoc, don vi va luu y truoc khi dung that.
-- Khong co lieu luong o day vi lieu phu thuoc tung ca.
--
-- ON CONFLICT DO NOTHING theo search_name nhu V7: thuoc da co thi khong ghi de.
INSERT INTO medications (name, search_name, unit, caution) VALUES
  ('Chloramphenicol (nhỏ mắt)', 'chloramphenicol (nho mat)', 'lọ', 'Dùng nhỏ mắt. Người cầm thuốc rửa tay sau khi dùng, phụ nữ mang thai nên tránh tiếp xúc. Không để đầu lọ chạm vào mắt.'),
  ('Oxytetracycline (mỡ mắt)', 'oxytetracycline (mo mat)', 'tuýp', 'Dùng tra mắt. Không để đầu tuýp chạm vào mắt.'),
  ('Prednisolone acetate (nhỏ mắt)', 'prednisolone acetate (nho mat)', 'lọ', 'Corticoid nhỏ mắt: không dùng khi mắt đang có loét giác mạc. Không tự ngừng đột ngột nếu đã dùng dài ngày.'),
  ('Dorzolamide (nhỏ mắt)', 'dorzolamide (nho mat)', 'lọ', 'Dùng nhỏ mắt hạ nhãn áp. Có thể gây xót mắt thoáng qua.'),
  ('Timolol (nhỏ mắt)', 'timolol (nho mat)', 'lọ', 'Dùng nhỏ mắt hạ nhãn áp. Thận trọng ở thú có bệnh tim hoặc hen.'),
  ('Latanoprost (nhỏ mắt)', 'latanoprost (nho mat)', 'lọ', 'Chủ yếu dùng cho chó. Không dùng khi thủy tinh thể bị lệch ra trước. Có thể làm co nhỏ đồng tử.'),
  ('Ciprofloxacin (nhỏ mắt)', 'ciprofloxacin (nho mat)', 'lọ', 'Dùng nhỏ mắt. Không để đầu lọ chạm vào mắt.'),
  ('Cyclosporine (nhỏ mắt)', 'cyclosporine (nho mat)', 'tuýp', 'Thường phải dùng lâu dài, hiệu quả có thể chỉ rõ sau vài tuần. Dùng đều đặn theo chỉ định của bác sĩ.'),
  ('Tacrolimus (nhỏ mắt)', 'tacrolimus (nho mat)', 'lọ', 'Dùng nhỏ mắt, thường khi Cyclosporine không đủ hiệu quả. Dùng đều đặn theo chỉ định của bác sĩ.'),
  ('Nước mắt nhân tạo', 'nuoc mat nhan tao', 'lọ', NULL),
  ('Cyclosporine', 'cyclosporine', 'viên', 'Thuốc ức chế miễn dịch: chỉ bác sĩ chuyên khoa chỉ định, theo dõi xét nghiệm định kỳ. Không tự ngừng thuốc. Cho uống đều đặn một cách nhất quán (cùng hoặc cách bữa ăn).'),
  ('Mycophenolate', 'mycophenolate', 'viên', 'Thuốc ức chế miễn dịch: chỉ bác sĩ chuyên khoa chỉ định, theo dõi xét nghiệm máu định kỳ. Có thể gây rối loạn tiêu hoá.'),
  ('Toceranib (Palladia)', 'toceranib (palladia)', 'viên', 'Thuốc độc tế bào: chỉ bác sĩ chuyên khoa chỉ định, theo dõi xét nghiệm máu định kỳ. Người cầm thuốc phải đeo găng, không bẻ hoặc nghiền viên thuốc.'),
  ('Cyclophosphamide', 'cyclophosphamide', 'viên', 'Thuốc độc tế bào: chỉ bác sĩ chuyên khoa chỉ định, theo dõi xét nghiệm máu định kỳ. Người cầm thuốc phải đeo găng, không bẻ hoặc nghiền viên thuốc. Cho thú uống nhiều nước để giảm nguy cơ viêm bàng quang xuất huyết.'),
  ('Doxorubicin', 'doxorubicin', 'lọ', 'Thuốc độc tế bào: chỉ bác sĩ chuyên khoa chỉ định, theo dõi xét nghiệm máu định kỳ. Người cầm thuốc phải đeo găng, không bẻ hoặc nghiền viên thuốc. Độc với tim, và với thận ở mèo. Thoát mạch gây hoại tử mô.'),
  ('Vincristine', 'vincristine', 'lọ', 'Thuốc độc tế bào: chỉ bác sĩ chuyên khoa chỉ định, theo dõi xét nghiệm máu định kỳ. Người cầm thuốc phải đeo găng, không bẻ hoặc nghiền viên thuốc. Thoát mạch gây hoại tử mô.'),
  ('Dextrose (glucose)', 'dextrose (glucose)', 'chai', 'Dung dịch đường ưu trương: truyền chậm theo chỉ định, thoát mạch gây hoại tử mô. Theo dõi đường huyết sau khi truyền.'),
  ('Sulfadimethoxine (Albon)', 'sulfadimethoxine (albon)', 'ml', 'Cho thú uống đủ nước. Thận trọng ở thú suy thận hoặc suy gan.'),
  ('Ponazuril', 'ponazuril', 'ml', 'Dùng theo chỉ định của bác sĩ; kiểm tra độ tuổi tối thiểu theo nhãn.'),
  ('Theophylline', 'theophylline', 'viên', 'Khoảng liều an toàn hẹp, quá liều gây tim nhanh, bồn chồn, co giật. Không tự tăng liều.'),
  ('DOCP (Zycortal)', 'docp (zycortal)', 'ml', 'Thuốc tiêm thay thế hormone, tiêm cách nhau khoảng 25 ngày theo chỉ định. Theo dõi điện giải (natri, kali) định kỳ.'),
  ('Fludrocortisone', 'fludrocortisone', 'viên', 'Theo dõi điện giải (natri, kali) và huyết áp. Không tự ngừng thuốc.'),
  ('Osaterone (Ypozane)', 'osaterone (ypozane)', 'viên', 'Chỉ dùng cho chó đực. Người cầm thuốc rửa tay sau khi dùng; phụ nữ mang thai hoặc đang cho con bú nên tránh tiếp xúc.'),
  ('Fluoxetine', 'fluoxetine', 'viên', 'Có thể mất vài tuần mới thấy hiệu quả. Không dùng chung với Clomipramine hoặc các thuốc ức chế MAO. Không tự ngừng đột ngột.'),
  ('Clomipramine', 'clomipramine', 'viên', 'Không dùng chung với Fluoxetine hoặc các thuốc ức chế MAO. Thận trọng ở thú có bệnh tim, có tiền sử co giật.'),
  ('Haloperidol', 'haloperidol', 'ml', 'Chỉ dùng cho chim khi bác sĩ chỉ định. Có thể gây an thần quá mức: theo dõi sát sau khi dùng.'),
  ('Feliway (pheromone)', 'feliway (pheromone)', 'chai', 'Dạng khuếch tán trong phòng hoặc xịt, không phải thuốc uống. Hiệu quả cần dùng liên tục vài tuần.'),
  ('Sildenafil', 'sildenafil', 'viên', 'Có thể gây hạ huyết áp, đỏ mặt. Thận trọng ở thú có bệnh tim.'),
  ('Amlodipine', 'amlodipine', 'viên', 'Theo dõi huyết áp định kỳ. Có thể làm nướu sưng nhẹ.'),
  ('Ursodiol', 'ursodiol', 'viên', 'Không dùng khi tắc ống mật hoàn toàn. Nên cho uống cùng thức ăn.'),
  ('Methylprednisolone acetate', 'methylprednisolone acetate', 'ống', 'Corticoid dạng tiêm tác dụng kéo dài. Không dùng chung với thuốc kháng viêm NSAID. Có thể làm tăng uống nước, đi tiểu nhiều.'),
  ('Celecoxib', 'celecoxib', 'viên', 'Thuốc kháng viêm NSAID: không dùng chung với corticoid hoặc NSAID khác. Ngưng thuốc và báo bác sĩ nếu thú nôn, bỏ ăn hoặc phân đen.'),
  ('Deslorelin (Suprelorin)', 'deslorelin (suprelorin)', 'que', 'Que cấy dưới da. Tác dụng có thể kéo dài nhiều tháng; cấy theo chỉ định của bác sĩ.'),
  ('Leuprolide', 'leuprolide', 'ống', 'Thuốc tiêm, dùng theo chỉ định của bác sĩ.'),
  ('Ketoconazole', 'ketoconazole', 'viên', 'Có thể gây độc gan: theo dõi men gan khi dùng dài ngày. Thận trọng ở thú bệnh gan. Cho uống cùng thức ăn.'),
  ('Dầu tắm Malaseb', 'dau tam malaseb', 'chai', 'Dùng ngoài da. Để dầu tắm trên da theo thời gian ghi trên nhãn rồi xả sạch. Tránh vùng mắt.'),
  ('Silver sulfadiazine', 'silver sulfadiazine', 'tuýp', 'Dùng ngoài da cho vết thương, vết bỏng. Không để thú liếm hoặc nuốt.'),
  ('Glucosamine', 'glucosamine', 'viên', NULL),
  ('Canxi (đường uống)', 'canxi (duong uong)', 'viên', 'Chỉ dùng theo chỉ định của bác sĩ: thừa canxi gây hại cho thận và xương.'),
  ('Nước muối sinh lý 0,9%', 'nuoc muoi sinh ly 0,9%', 'chai', NULL)
ON CONFLICT (search_name) DO NOTHING;
