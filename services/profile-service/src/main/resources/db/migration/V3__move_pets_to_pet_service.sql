-- VD-10: hồ sơ thú cưng chuyển sang pet-service (pet_db.pets).
--
-- Bảng cũ KHÔNG bị DROP, chỉ đổi tên thành _backup: dữ liệu đã chạy thật nên nếu bước di trú
-- sang pet_db có sai sót thì vẫn còn bản gốc để đối chiếu. Xoá hẳn là việc của một lần dọn về sau,
-- sau khi đã xác nhận pet_db đủ và đúng.
--
-- Đổi tên trước cũng là cách để lộ ngay nếu đâu đó trong profile-service còn đọc bảng này:
-- lỗi "relation pets does not exist" rõ hơn nhiều so với một bảng lặng lẽ rỗng dần.
ALTER TABLE pets RENAME TO pets_moved_to_pet_service_backup;
