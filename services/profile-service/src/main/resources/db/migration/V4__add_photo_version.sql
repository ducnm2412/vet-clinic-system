-- Anh dai dien (bac si, nhan vien, khach hang; anh thu cung chua ho tro sau khi Pet chuyen sang pet-service). File nam tren o dia cua profile-service;
-- DB chi giu so lan da tai anh len (0 = chua co anh), dung de dung URL va pha cache trinh duyet.
ALTER TABLE doctor_profiles ADD COLUMN IF NOT EXISTS photo_version INT NOT NULL DEFAULT 0;
ALTER TABLE staff_profiles ADD COLUMN IF NOT EXISTS photo_version INT NOT NULL DEFAULT 0;
ALTER TABLE customer_profiles ADD COLUMN IF NOT EXISTS photo_version INT NOT NULL DEFAULT 0;
