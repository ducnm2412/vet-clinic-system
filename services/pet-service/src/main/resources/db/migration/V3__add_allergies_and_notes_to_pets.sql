-- VD-22: hai thông tin mà cả chủ nuôi lẫn bác sĩ đều cần trước khi khám.
--
-- allergies để riêng chứ không gộp vào notes: dị ứng thuốc là thông tin an toàn, phải tra được
-- thành một trường riêng để màn hình khám của bác sĩ hiện nổi bật trước khi kê đơn, chứ không
-- nằm lẫn trong một đoạn ghi chú dài.
ALTER TABLE pets ADD COLUMN allergies TEXT;
ALTER TABLE pets ADD COLUMN notes TEXT;
