#!/usr/bin/env bash
# Thêm dữ liệu mẫu: 5 bác sĩ + 5 nhân viên (tài khoản ở auth_db, hồ sơ ở profile_db).
# Chạy lại được nhiều lần: tài khoản đã có (theo email) thì bỏ qua.
#
# Mật khẩu: KHÔNG đặt mật khẩu mới. Các tài khoản mẫu dùng chung mật khẩu của một tài khoản nhân viên đã có
# trong auth_db (sao chép mã băm lúc chạy), nên đăng nhập bằng mật khẩu của tài khoản nhân viên đó.
#
# Cần các container auth-db và profile-db đang chạy:  bash scripts/seed-demo-staff.sh
set -euo pipefail

HASH=$(docker exec auth-db psql -U postgres -d auth_db -Atc \
  "SELECT u.password_hash FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
   WHERE r.name = 'STAFF' AND u.password_hash IS NOT NULL ORDER BY u.created_at LIMIT 1")
if [ -z "$HASH" ]; then
  echo "Chưa có tài khoản STAFF nào có mật khẩu trong auth_db để lấy mẫu. Hãy tạo một nhân viên trước." >&2
  exit 1
fi

# id cố định để chạy lại không sinh trùng và hồ sơ khớp tài khoản.
# email | họ+đệm | tên | role | id
ACCOUNTS=$(cat <<'LIST'
bacsi.khoa@vetclinic.local|Nguyễn Minh|Khoa|DOCTOR|b0000000-0000-4000-8000-000000000001
bacsi.ha@vetclinic.local|Trần Thu|Hà|DOCTOR|b0000000-0000-4000-8000-000000000002
bacsi.dung@vetclinic.local|Lê Quang|Dũng|DOCTOR|b0000000-0000-4000-8000-000000000003
bacsi.anh@vetclinic.local|Phạm Ngọc|Anh|DOCTOR|b0000000-0000-4000-8000-000000000004
bacsi.tung@vetclinic.local|Võ Thanh|Tùng|DOCTOR|b0000000-0000-4000-8000-000000000005
nhanvien.mai@vetclinic.local|Đỗ Thị|Mai|STAFF|b0000000-0000-4000-8000-000000000011
nhanvien.long@vetclinic.local|Bùi Văn|Long|STAFF|b0000000-0000-4000-8000-000000000012
nhanvien.lan@vetclinic.local|Hoàng Thị|Lan|STAFF|b0000000-0000-4000-8000-000000000013
nhanvien.minh@vetclinic.local|Ngô Đức|Minh|STAFF|b0000000-0000-4000-8000-000000000014
nhanvien.trang@vetclinic.local|Đặng Thu|Trang|STAFF|b0000000-0000-4000-8000-000000000015
LIST
)

AUTH_SQL="BEGIN;"
while IFS='|' read -r email first last role id; do
  AUTH_SQL+="
INSERT INTO users (id, email, password_hash, status, first_name, last_name)
  VALUES ('$id', '$email', :'hash', 'ACTIVE', '$first', '$last') ON CONFLICT (email) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
  SELECT '$id', r.id FROM roles r WHERE r.name = '$role'
  AND EXISTS (SELECT 1 FROM users WHERE id = '$id') ON CONFLICT DO NOTHING;"
done <<< "$ACCOUNTS"
AUTH_SQL+="
COMMIT;"
echo "$AUTH_SQL" | docker exec -i auth-db psql -U postgres -d auth_db -v ON_ERROR_STOP=1 -v hash="$HASH" -q

# Hồ sơ: bác sĩ cần chuyên khoa, nhân viên cần chức vụ (cột bắt buộc).
docker exec -i profile-db psql -U postgres -d profile_db -v ON_ERROR_STOP=1 -q <<'SQL'
BEGIN;
INSERT INTO doctor_profiles (user_id, full_name, specialty, phone, years_of_experience) VALUES
  ('b0000000-0000-4000-8000-000000000001', 'Nguyễn Minh Khoa', 'Nội tổng quát',          '0901000001', 8),
  ('b0000000-0000-4000-8000-000000000002', 'Trần Thu Hà',      'Ngoại khoa - phẫu thuật', '0901000002', 10),
  ('b0000000-0000-4000-8000-000000000003', 'Lê Quang Dũng',    'Da liễu thú y',           '0901000003', 6),
  ('b0000000-0000-4000-8000-000000000004', 'Phạm Ngọc Anh',    'Chẩn đoán hình ảnh',      '0901000004', 5),
  ('b0000000-0000-4000-8000-000000000005', 'Võ Thanh Tùng',    'Dinh dưỡng và tiêm phòng', '0901000005', 4)
ON CONFLICT (user_id) DO NOTHING;
INSERT INTO staff_profiles (user_id, position, phone, hire_date) VALUES
  ('b0000000-0000-4000-8000-000000000011', 'Lễ tân',              '0902000001', '2026-01-05'),
  ('b0000000-0000-4000-8000-000000000012', 'Thu ngân',            '0902000002', '2026-01-05'),
  ('b0000000-0000-4000-8000-000000000013', 'Nhân viên kho',       '0902000003', '2026-02-10'),
  ('b0000000-0000-4000-8000-000000000014', 'Chăm sóc khách hàng', '0902000004', '2026-03-01'),
  ('b0000000-0000-4000-8000-000000000015', 'Nhân viên bán hàng',  '0902000005', '2026-03-01')
ON CONFLICT (user_id) DO NOTHING;
COMMIT;
SQL

echo "Xong. Bác sĩ: $(docker exec auth-db psql -U postgres -d auth_db -Atc "SELECT count(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.name='DOCTOR'")," \
     "nhân viên: $(docker exec auth-db psql -U postgres -d auth_db -Atc "SELECT count(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.name='STAFF'")"
