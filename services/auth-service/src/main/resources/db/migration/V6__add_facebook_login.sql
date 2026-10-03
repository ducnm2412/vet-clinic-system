-- Dang nhap bang Facebook, cung kieu voi google_sub (V5): facebook_id la dinh danh on dinh
-- Facebook cap, UNIQUE nhung cho phep NULL vi tai khoan dang ky bang mat khau/Google tu truoc
-- khong co.
ALTER TABLE users ADD COLUMN facebook_id VARCHAR(255) UNIQUE;
