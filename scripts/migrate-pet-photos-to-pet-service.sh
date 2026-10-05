#!/usr/bin/env bash
#
# Chuyển ảnh thú cưng từ profile-service sang pet-service.
#
# Ảnh cũ nằm trong volume của profile-service (/data/uploads/pets/<petId>.<ext>), còn số phiên bản
# ảnh (photo_version) nằm trong bảng thú cưng cũ của profile_db. Hai thứ đó phải sang pet-service:
# file chép vào volume pet-uploads-data, photo_version ghi vào pet_db.pets.
#
# Chạy được nhiều lần: chỉ ghi photo_version cho con nào còn bằng 0, file chép đè cùng nội dung.
# Chạy SAU migrate-pets-to-pet-service.sh, khi profile-service, pet-service, profile-db, pet-db đang lên:
#
#   bash scripts/migrate-pet-photos-to-pet-service.sh
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

for c in profile-db pet-db profile-service pet-service; do
  docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null | grep -q true || { echo "Container $c chưa chạy"; exit 1; }
done

SOURCE=""
for candidate in pets_moved_to_pet_service_backup pets; do
  if psql_profile -tAc "SELECT to_regclass('public.$candidate') IS NOT NULL" | grep -q t; then
    SOURCE="$candidate"; break
  fi
done
[ -n "$SOURCE" ] || { echo "profile_db không còn bảng thú cưng nào — bỏ qua"; exit 0; }

# Bảng nguồn có thể chưa có cột photo_version nếu profile-service chưa từng chạy bản có ảnh.
if ! psql_profile -tAc "SELECT 1 FROM information_schema.columns WHERE table_name='$SOURCE' AND column_name='photo_version'" | grep -q 1; then
  echo "profile_db.$SOURCE không có cột photo_version — không có ảnh nào để chuyển"
  exit 0
fi

# 1) File ảnh: profile-service -> pet-service, qua tar để giữ nguyên tên file và không cần đường dẫn trên máy chủ.
if docker exec profile-service test -d /data/uploads/pets; then
  docker exec pet-service mkdir -p /data/uploads/pets
  docker exec profile-service tar -C /data/uploads/pets -cf - . | docker exec -i pet-service tar -C /data/uploads/pets -xf -
  echo "Đã chép $(docker exec pet-service sh -c 'ls /data/uploads/pets | wc -l') file ảnh sang pet-service"
else
  echo "profile-service không có thư mục ảnh thú cưng — chỉ chuyển photo_version"
fi

# 2) photo_version: chỉ những con thật sự có ảnh. Ghi đúng giá trị cũ để URL ảnh không đổi.
UPDATED=0
while IFS='|' read -r id version; do
  [ -n "$id" ] || continue
  n=$(psql_pet -v ON_ERROR_STOP=1 -tAc "WITH u AS (UPDATE pets SET photo_version = $version WHERE id = '$id' AND photo_version = 0 RETURNING 1) SELECT count(*) FROM u")
  UPDATED=$((UPDATED + n))
done < <(psql_profile -tAc "SELECT id, photo_version FROM $SOURCE WHERE photo_version > 0")

echo "pet_db.pets: cập nhật photo_version cho $UPDATED con"
echo "File ảnh cũ trong volume của profile-service vẫn còn nguyên."
