-- VD-20: ho ten bac si de hien tren trang cong khai va lich hen.
-- Ban goc nam o auth-service; profile-service luu mot ban nhan qua su kien user.staff-created.
-- Cho phep NULL: ho so tao truoc khi co cot nay chua co ten, giao dien roi ve chuyen mon.
ALTER TABLE doctor_profiles ADD COLUMN full_name VARCHAR(200);
