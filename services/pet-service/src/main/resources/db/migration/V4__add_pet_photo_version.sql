-- Ảnh đại diện thú cưng. File nằm trên đĩa (xem PetPhotoStorageService), DB chỉ giữ số phiên bản:
-- 0 = chưa có ảnh, mỗi lần tải ảnh mới tăng 1 để URL đổi theo và trình duyệt bỏ cache bản cũ.
ALTER TABLE pets ADD COLUMN IF NOT EXISTS photo_version INT NOT NULL DEFAULT 0;
