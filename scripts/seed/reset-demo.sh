#!/usr/bin/env bash
# 시연 리셋 (db 서버에서 실행): 백업 → 시드 다시 넣기 → 개수 확인
# 사용: bash ~/reset-demo.sh            (시드 파일 기본 위치 ~/R__seed_demo.sql)
#       bash ~/reset-demo.sh 다른경로.sql
# 앱 서버는 재시작하지 않아도 됨. 로그인 세션은 다시 로그인 필요할 수 있음
set -euo pipefail
SEED="${1:-$HOME/R__seed_demo.sql}"
[ -f "$SEED" ] || { echo "시드 파일이 없습니다: $SEED"; exit 1; }

read -s -p "MySQL root 비밀번호: " DBPW; echo
mkdir -p "$HOME/backup"
TS=$(date +%m%d_%H%M)

echo "1/3 백업 → ~/backup/before_reset_$TS.sql"
sudo docker exec -e MYSQL_PWD="$DBPW" wms-db mysqldump -uroot --single-transaction \
  --default-character-set=utf8mb4 wms_db > "$HOME/backup/before_reset_$TS.sql"

echo "2/3 시드 실행"
sudo docker exec -i -e MYSQL_PWD="$DBPW" wms-db mysql -uroot --default-character-set=utf8mb4 wms_db < "$SEED"

echo "3/3 확인"
sudo docker exec -e MYSQL_PWD="$DBPW" wms-db mysql -uroot wms_db -e "
SELECT (SELECT COUNT(*) FROM document) AS 문서, (SELECT COUNT(*) FROM stock WHERE qty > 0) AS 재고행,
       (SELECT COUNT(*) FROM location) AS 칸, (SELECT COUNT(*) FROM user_account) AS 계정,
       (SELECT COUNT(*) FROM picking_list) AS 피킹리스트, (SELECT COUNT(*) FROM stock_disposal) AS 폐기;"
echo "끝. 기대값: 문서 327 · 재고행 243 · 칸 120 · 계정 4 · 피킹리스트 21 · 폐기 8"