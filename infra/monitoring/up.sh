#!/bin/bash
# 모니터링 스택 기동. Grafana 관리자 비밀번호를 파라미터 스토어에서 받아 넘긴다.
#   사용: infra/monitoring/up.sh                 모든 서비스 기동 (k6 서버, /root/Backend 를 origin/develop 에 맞춘 뒤)
#         infra/monitoring/up.sh grafana         Grafana 만
#         infra/monitoring/up.sh reset-password  떠 있는 Grafana 의 관리자 비밀번호를 파라미터 값으로 재설정
# 나머지 비밀(PG_EXPORTER_*)은 같은 폴더의 .env 에서 compose 가 읽는다.
set -euo pipefail
cd "$(dirname "$0")"
command -v aws >/dev/null || { echo "aws CLI 가 없습니다. 한 번만 설치: apt-get install -y unzip && curl -sS https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip && unzip -qo /tmp/awscliv2.zip -d /tmp && /tmp/aws/install" >&2; exit 1; }
export GRAFANA_PASSWORD
GRAFANA_PASSWORD=$(aws ssm get-parameter --region ap-northeast-2 \
  --name /moyeota/monitoring/GRAFANA_PASSWORD --with-decryption \
  --query Parameter.Value --output text)
if [ "${1:-}" = "reset-password" ]; then
  # 이미 떠 있는 Grafana 는 환경변수만으로 비밀번호가 안 바뀐다(최초 기동 때만 적용). 파라미터 값으로 맞춘다
  docker compose exec grafana grafana cli admin reset-admin-password "$GRAFANA_PASSWORD"
  exit 0
fi
docker compose up -d "$@"
