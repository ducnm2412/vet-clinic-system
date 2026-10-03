-- Dang nhap bang Google. google_sub la dinh danh on dinh Google cap cho tung tai khoan,
-- khong doi du nguoi dung co doi email that hay khong - dung no de tim lai tai khoan thay vi
-- email. UNIQUE nhung cho phep NULL vi tai khoan dang ky bang mat khau tu truoc khong co.
ALTER TABLE users ADD COLUMN google_sub VARCHAR(255) UNIQUE;

-- Tai khoan tao qua Google khong dat mat khau.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
