-- Ảnh thú cưng lưu trên Cloudinary: DB giữ URL (đã kèm số phiên bản của Cloudinary nên đổi ảnh là đổi URL).
-- NULL nghĩa là chưa có ảnh trên Cloudinary — con nào còn photo_version > 0 thì vẫn đọc ảnh cũ trên đĩa
-- cho tới khi được chuyển lên (xem LegacyPhotoMigration).
ALTER TABLE pets ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500);
