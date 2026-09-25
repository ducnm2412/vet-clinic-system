#!/usr/bin/env bash
#
# VD-10 chặng 2: chuyển bệnh án và đơn thuốc từ booking_db sang pet_db.
#
# booking_db lưu bệnh án trỏ vào appointments; pet_db lưu kèm pet_id, customer_user_id và
# doctor_user_id ngay trên bệnh án — nên phải join appointments và appointment_slots để lấy ba
# trường đó. Giữ NGUYÊN id của bệnh án: payment_db.payments.medical_record_id đang trỏ tới.
#
# Chạy được nhiều lần: id đã có trong pet_db thì bỏ qua (ON CONFLICT DO NOTHING).
#
# Bệnh án của con vật không còn trong pet_db sẽ bị BỎ LẠI và báo số lượng — khoá ngoại pet_id
# không cho phép chèn. Chạy scripts/migrate-pets-to-pet-service.sh trước.
#
#   bash scripts/migrate-medical-records-to-pet-service.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$ROOT/.env" ] || { echo "Không thấy $ROOT/.env"; exit 1; }
while IFS= read -r line; do
  case "$line" in ''|\#*) continue;; esac
  export "${line%%=*}=${line#*=}"
done < "$ROOT/.env"

psql_booking() { docker exec -i -e PGPASSWORD="${BOOKING_DB_PASSWORD:-postgres}" booking-db psql -U "${BOOKING_DB_USER:-postgres}" -d booking_db "$@"; }
psql_pet() { docker exec -i -e PGPASSWORD="${PET_DB_PASSWORD:-postgres}" pet-db psql -U "${PET_DB_USER:-postgres}" -d pet_db "$@"; }

for c in booking-db pet-db; do
  docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null | grep -q true || { echo "Container $c chưa chạy"; exit 1; }
done

if ! psql_pet -tAc "SELECT to_regclass('public.medical_records') IS NOT NULL" | grep -q t; then
  echo "pet_db chưa có bảng medical_records — bật pet-service một lần cho Flyway tạo schema trước:"
  echo "  docker compose up -d --build pet-service"
  exit 1
fi

# booking-service đã có migration đổi tên hai bảng cũ thành _backup. Tuỳ thứ tự triển khai, bảng
# nguồn có thể còn tên cũ hoặc đã là tên backup.
RECORDS=""
for candidate in medical_records_moved_to_pet_service_backup medical_records; do
  if psql_booking -tAc "SELECT to_regclass('public.$candidate') IS NOT NULL" | grep -q t; then
    RECORDS="$candidate"; break
  fi
done
[ -n "$RECORDS" ] || { echo "booking_db không còn bảng bệnh án nào — bỏ qua"; exit 0; }
ITEMS="prescription_items"
psql_booking -tAc "SELECT to_regclass('public.prescription_items_moved_to_pet_service_backup') IS NOT NULL" | grep -q t \
  && ITEMS="prescription_items_moved_to_pet_service_backup"
echo "Bảng nguồn: booking_db.$RECORDS + booking_db.$ITEMS"

BEFORE_R=$(psql_pet -tAc "SELECT count(*) FROM medical_records")
BEFORE_I=$(psql_pet -tAc "SELECT count(*) FROM prescription_items")

TMP_R="$(mktemp)"; TMP_I="$(mktemp)"
trap 'rm -f "$TMP_R" "$TMP_I"' EXIT

psql_booking -tAc "\copy (
  SELECT m.id, m.appointment_id, a.pet_id, a.customer_user_id, s.doctor_user_id,
         m.diagnosis, m.treatment, m.notes, m.status, m.created_at, m.updated_at
  FROM $RECORDS m
  JOIN appointments a ON a.id = m.appointment_id
  JOIN appointment_slots s ON s.id = a.slot_id
) TO STDOUT WITH (FORMAT csv)" > "$TMP_R"

psql_booking -tAc "\copy (
  SELECT i.id, i.medical_record_id, i.medication_name, i.dosage, i.frequency, i.duration_days,
         i.notes, i.created_at, i.updated_at
  FROM $ITEMS i
) TO STDOUT WITH (FORMAT csv)" > "$TMP_I"

echo "Đọc được $(grep -c . "$TMP_R" || true) bệnh án, $(grep -c . "$TMP_I" || true) dòng thuốc"
[ "$(grep -c . "$TMP_R" || true)" -gt 0 ] || { echo "Không có gì để chuyển"; exit 0; }

psql_pet -v ON_ERROR_STOP=1 -c "
CREATE TABLE IF NOT EXISTS medical_records_incoming (LIKE medical_records);
CREATE TABLE IF NOT EXISTS prescription_items_incoming (LIKE prescription_items);
TRUNCATE medical_records_incoming; TRUNCATE prescription_items_incoming;"

# \copy là lệnh của psql, không phải SQL: phải đứng một mình trong -c.
psql_pet -v ON_ERROR_STOP=1 -c "\copy medical_records_incoming (id, appointment_id, pet_id, customer_user_id, doctor_user_id, diagnosis, treatment, notes, status, created_at, updated_at) FROM STDIN WITH (FORMAT csv)" < "$TMP_R"
psql_pet -v ON_ERROR_STOP=1 -c "\copy prescription_items_incoming (id, medical_record_id, medication_name, dosage, frequency, duration_days, notes, created_at, updated_at) FROM STDIN WITH (FORMAT csv)" < "$TMP_I"

# Bệnh án của con vật không còn hồ sơ trong pet_db: báo ra rồi bỏ lại, đừng để khoá ngoại làm
# đứt cả lượt di trú.
ORPHANS=$(psql_pet -tAc "SELECT count(*) FROM medical_records_incoming i
  WHERE NOT EXISTS (SELECT 1 FROM pets p WHERE p.id = i.pet_id)")
[ "$ORPHANS" = "0" ] || echo "BỎ LẠI $ORPHANS bệnh án vì thú cưng không còn trong pet_db (chạy migrate-pets-to-pet-service.sh trước)"

psql_pet -v ON_ERROR_STOP=1 -c "
INSERT INTO medical_records
SELECT i.* FROM medical_records_incoming i
WHERE EXISTS (SELECT 1 FROM pets p WHERE p.id = i.pet_id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO prescription_items
SELECT i.* FROM prescription_items_incoming i
WHERE EXISTS (SELECT 1 FROM medical_records m WHERE m.id = i.medical_record_id)
ON CONFLICT (id) DO NOTHING;

DROP TABLE medical_records_incoming; DROP TABLE prescription_items_incoming;"

AFTER_R=$(psql_pet -tAc "SELECT count(*) FROM medical_records")
AFTER_I=$(psql_pet -tAc "SELECT count(*) FROM prescription_items")
echo "pet_db.medical_records:   $BEFORE_R -> $AFTER_R (thêm $((AFTER_R - BEFORE_R)))"
echo "pet_db.prescription_items: $BEFORE_I -> $AFTER_I (thêm $((AFTER_I - BEFORE_I)))"
echo "Hai bảng cũ trong booking_db vẫn còn nguyên để đối chiếu."
