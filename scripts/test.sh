#!/usr/bin/env bash
# Chạy test của một service (hoặc tất cả) với đúng biến môi trường trong .env.
#
#   bash scripts/test.sh                      # tất cả service
#   bash scripts/test.sh booking-service      # một service
#   bash scripts/test.sh auth-service -Dtest=AccountLockTest
#
# Database test (*_test) và múi giờ đã khai sẵn trong pom.xml từng service, nên script này chỉ
# lo mật khẩu database và RabbitMQ. Test không bao giờ chạy vào database thật — TestDatabaseGuard
# dừng ngay nếu tên database không kết thúc bằng "_test" (VD-12).
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ ! -f "$ROOT/.env" ]; then
  echo "Khong tim thay .env — chep .env.example roi dien mat khau truoc."
  exit 2
fi

# Đọc từng dòng thay vì `source`: giá trị có dấu cách (vd BOOTSTRAP_ADMIN_NAME) sẽ làm `source` hỏng.
while IFS= read -r line; do
  case "$line" in ''|\#*) continue;; esac
  export "${line%%=*}=${line#*=}"
done < "$ROOT/.env"

export RABBITMQ_USERNAME="${RABBITMQ_USER:-guest}"

run_one() {
  svc=$1; shift
  # Xoá báo cáo cũ: còn sót thì lượt sau đếm nhầm cả test của lần chạy trước.
  rm -rf "$ROOT/services/$svc/target/surefire-reports"
  prefix=$(echo "$svc" | sed 's/-service//' | tr 'a-z' 'A-Z')
  user_var="${prefix}_DB_USER"; pass_var="${prefix}_DB_PASSWORD"
  DB_USERNAME="${!user_var:-postgres}" DB_PASSWORD="${!pass_var:-postgres}" \
    mvn -q -f "$ROOT/services/$svc/pom.xml" test "$@"
  code=$?
  total=$(grep -h "Tests run" "$ROOT/services/$svc"/target/surefire-reports/*.txt 2>/dev/null |
    awk '{t+=$3; f+=$5; e+=$7} END {printf "%d test, %d fail, %d error", t, f, e}')
  [ $code -eq 0 ] && echo "OK    $svc — $total" || echo "LOI   $svc — $total (xem target/surefire-reports)"
  return $code
}

if [ $# -gt 0 ] && [ -d "$ROOT/services/$1" ]; then
  svc=$1; shift
  run_one "$svc" "$@"
else
  status=0
  for svc in auth-service profile-service product-service order-service booking-service \
             payment-service staff-service pet-service notification-service reporting-service; do
    run_one "$svc" "$@" || status=1
  done
  exit $status
fi
