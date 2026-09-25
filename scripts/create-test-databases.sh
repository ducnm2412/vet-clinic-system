#!/usr/bin/env bash
# VD-12: dựng database riêng cho test, mỗi service một cái, nằm cùng container với database thật.
#
# Chạy một lần sau khi `docker compose up -d`:
#   bash scripts/create-test-databases.sh
#
# Sau đó `mvn test` trong bất kỳ service nào cũng tự trỏ vào database *_test (khai trong pom.xml).
# Test KHÔNG BAO GIỜ được chạy vào database thật — xem TestDatabaseGuard và VD-12.
set -u

# container:database — cổng không cần vì chạy psql ngay trong container.
TARGETS="auth-db:auth_db profile-db:profile_db product-db:product_db order-db:order_db booking-db:booking_db payment-db:payment_db staff-db:staff_db pet-db:pet_db"

status=0
for target in $TARGETS; do
  container=${target%%:*}
  db=${target##*:}_test

  if ! docker ps --format '{{.Names}}' | grep -qx "$container"; then
    echo "BO QUA  $container chua chay (docker compose up -d $container)"
    status=1
    continue
  fi

  user=$(docker exec "$container" printenv POSTGRES_USER 2>/dev/null || echo postgres)
  if docker exec "$container" psql -U "$user" -lqt | cut -d'|' -f1 | grep -qw "$db"; then
    echo "DA CO   $db"
  elif docker exec "$container" createdb -U "$user" "$db"; then
    echo "DA TAO  $db"
  else
    echo "LOI     khong tao duoc $db trong $container"
    status=1
  fi
done

echo
echo "Flyway se tu tao bang trong database test o lan chay test dau tien."
exit $status
