#!/usr/bin/env bash
# Khoi dong he thong THEO THU TU UU TIEN, tung dot mot, thay vi "docker compose up -d" bat het cung luc.
# Moi dot chi bat dau khi dot truoc da healthy -> tranh dinh RAM/CPU luc nhieu JVM cung khoi dong.
#
#   bash scripts/start.sh          # bat toan bo theo thu tu
#   bash scripts/start.sh 3        # chi bat tu dot 3 tro di (cac dot truoc coi nhu da chay)
set -euo pipefail
cd "$(dirname "$0")/.."

WAVES=(
  "auth-db profile-db pet-db booking-db product-db order-db payment-db staff-db rabbitmq redis mailhog"  # 1. Ha tang
  "eureka-server"                                                                                         # 2. Service registry
  "api-gateway auth-service"                                                                              # 3. Cong vao + dang nhap
  "profile-service pet-service product-service booking-service"                                          # 4. Nghiep vu chinh
  "order-service payment-service"                                                                         # 5. Mua hang, thanh toan
  "staff-service notification-service reporting-service"                                                 # 6. Phu tro
  "frontend"                                                                                              # 7. Giao dien
)

FROM=${1:-1}
for i in "${!WAVES[@]}"; do
  n=$((i + 1))
  [ "$n" -lt "$FROM" ] && continue
  echo "==> Dot $n/${#WAVES[@]}: ${WAVES[$i]}"
  # --no-deps: khong de compose tu keo theo service khac; --wait: cho healthy moi di tiep.
  # Service loi thi bao va DI TIEP (khong de 1 service phu chan ca he thong).
  # shellcheck disable=SC2086
  if ! docker compose up -d --no-deps --wait --wait-timeout 300 ${WAVES[$i]}; then
    echo "!! Dot $n co service chua healthy - xem: docker compose ps / docker compose logs <service>"
  fi
done

docker compose ps
free -h 2>/dev/null || true
