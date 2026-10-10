#!/bin/bash
# 모니터링 스택 기동. Grafana 관리자 비밀번호를 파라미터 스토어에서 받아 넘긴다.
#   사용: cd infra/monitoring && ./up.sh            (k6 서버, /root/Backend 에서 git pull 뒤)
# 나머지 비밀(PG_EXPORTER_*)은 같은 폴더의 .env 에서 compose 가 읽는다.
set -euo pipefail
cd "$(dirname "$0")"
command -v aws >/dev/null || { echo "aws CLI 가 없습니다. 한 번만 설치: apt-get install -y unzip && curl -sS https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip && unzip -qo /tmp/awscliv2.zip -d /tmp && /tmp/aws/install" >&2; exit 1; }
export GRAFANA_PASSWORD
GRAFANA_PASSWORD=$(aws ssm get-parameter --region ap-northeast-2 \
  --name /moyeota/monitoring/GRAFANA_PASSWORD --with-decryption \
  --query Parameter.Value --output text)
docker compose up -d "$@"
