#!/usr/bin/env bash
#
# VD-10: chuyển hồ sơ thú cưng từ profile_db sang pet_db.
#
# profile_db lưu thú cưng theo customer_profile_id, pet_db lưu theo owner_user_id — nên phải
# join qua customer_profiles để đổi khoá. Giữ NGUYÊN id của từng con vật: booking_db và các bệnh án
# đang trỏ tới id đó, đổi id là mất liên kết.
#
# Chạy được nhiều lần: đã có id nào trong pet_db thì bỏ qua id đó (ON CONFLICT DO NOTHING).
# Chạy khi cả pet-db và profile-db đang lên:
#
#   bash scripts/migrate-pets-to-pet-service.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$ROOT/.env" ] || { echo "Không thấy $ROOT/.env"; exit 1; }
while IFS= read -r line; do
  case "$line" in ''|\#*) continue;; esac
  export "${line%%=*}=${line#*=}"
done < "$ROOT/.env"

PROFILE_USER="${PROFILE_DB_USER:-postgres}"
PET_USER="${PET_DB_USER:-postgres}"

psql_profile() { docker exec -i -e PGPASSWORD="${PROFILE_DB_PASSWORD:-postgres}" profile-db psql -U "$PROFILE_USER" -d profile_db "$@"; }
psql_pet() { docker exec -i -e PGPASSWORD="${PET_DB_PASSWORD:-postgres}" pet-db psql -U "$PET_USER" -d pet_db "$@"; }

for c in profile-db pet-db; do
  docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null | grep -q true || { echo "Container $c chưa chạy"; exit 1; }
done

# profile-service đã có migration đổi tên bảng cũ thành _backup. Tuỳ thứ tự triển khai, bảng nguồn
# có thể còn tên cũ hoặc đã là tên backup — thử tên backup trước vì đó là trạng thái sau khi deploy.
SOURCE=""
for candidate in pets_moved_to_pet_service_backup pets; do
  if psql_profile -tAc "SELECT to_regclass('public.$candidate') IS NOT NULL" | grep -q t; then
    SOURCE="$candidate"; break
  fi
done
[ -n "$SOURCE" ] && echo "Bảng nguồn: profile_db.$SOURCE" || { echo "profile_db không còn bảng thú cưng nào — bỏ qua"; exit 0; }

# pet_db chỉ có bảng sau khi pet-service chạy lần đầu (Flyway). Nói rõ thay vì để psql báo
# "relation pets does not exist" giữa lúc di trú.
if ! psql_pet -tAc "SELECT to_regclass('public.pets') IS NOT NULL" | grep -q t; then
  echo "pet_db chưa có bảng pets — bật pet-service một lần cho Flyway tạo schema trước:"
  echo "  docker compose up -d pet-service"
  exit 1
fi

BEFORE=$(psql_pet -tAc "SELECT count(*) FROM pets")

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
psql_profile -tAc "\copy (
  SELECT p.id, cp.user_id, p.name, p.species, p.breed, p.gender, p.date_of_birth,
         p.weight_kg, p.created_at, p.updated_at
  FROM $SOURCE p JOIN customer_profiles cp ON cp.id = p.customer_profile_id
) TO STDOUT WITH (FORMAT csv)" > "$TMP"

ROWS=$(grep -c . "$TMP" || true)
echo "Đọc được $ROWS dòng từ profile_db"
[ "$ROWS" -gt 0 ] || { echo "Không có gì để chuyển"; exit 0; }

# Nạp vào bảng tạm rồi mới INSERT ... ON CONFLICT: COPY không biết bỏ qua trùng khoá.
psql_pet -v ON_ERROR_STOP=1 -c "CREATE TABLE IF NOT EXISTS pets_incoming (LIKE pets); TRUNCATE pets_incoming;"
# \copy là lệnh của psql, không phải SQL: phải đứng một mình trong -c, không ghép cùng câu khác.
psql_pet -v ON_ERROR_STOP=1 -c "\copy pets_incoming (id, owner_user_id, name, species, breed, gender, date_of_birth, weight_kg, created_at, updated_at) FROM STDIN WITH (FORMAT csv)" < "$TMP"
psql_pet -v ON_ERROR_STOP=1 -c "
INSERT INTO pets SELECT * FROM pets_incoming ON CONFLICT (id) DO NOTHING;
DROP TABLE pets_incoming;
"

AFTER=$(psql_pet -tAc "SELECT count(*) FROM pets")
echo "pet_db.pets: $BEFORE -> $AFTER (thêm $((AFTER - BEFORE)))"
echo "Bảng cũ profile_db.$SOURCE vẫn còn nguyên để đối chiếu."
