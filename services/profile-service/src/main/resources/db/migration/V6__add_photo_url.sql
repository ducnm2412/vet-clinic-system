-- Ảnh đại diện lưu trên Cloudinary: DB giữ URL (đã kèm số phiên bản của Cloudinary nên đổi ảnh là đổi URL).
-- NULL nghĩa là chưa có ảnh trên Cloudinary — hồ sơ nào còn photo_version > 0 thì vẫn đọc ảnh cũ trên đĩa
-- cho tới khi được chuyển lên (xem LegacyPhotoMigration).
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);
ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);
