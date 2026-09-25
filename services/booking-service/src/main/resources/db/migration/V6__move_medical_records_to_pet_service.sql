-- VD-10 chặng 2: bệnh án và đơn thuốc chuyển sang pet-service (pet_db).
--
-- Không DROP: dữ liệu lâm sàng đã chạy thật, giữ lại để đối chiếu sau khi di trú. Đổi tên cả hai
-- bảng nên khoá ngoại prescription_items -> medical_records vẫn còn nguyên.
--
-- Đổi tên trước cũng là cách để lộ ngay nếu đâu đó trong booking-service còn đọc hai bảng này.
ALTER TABLE prescription_items RENAME TO prescription_items_moved_to_pet_service_backup;
ALTER TABLE medical_records RENAME TO medical_records_moved_to_pet_service_backup;
