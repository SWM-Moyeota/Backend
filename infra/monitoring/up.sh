#!/bin/bash
# 모니터링 스택 기동. Grafana 관리자 비밀번호를 파라미터 스토어에서 받아 넘긴다.
#   사용: infra/monitoring/up.sh                 모든 서비스 기동 (admin 서버, 이 폴더를 S3 에서 받은 뒤 - 아래 "서버에 올리기")
#         infra/monitoring/up.sh grafana         Grafana 만
#         infra/monitoring/up.sh reset-password  떠 있는 Grafana 의 관리자 비밀번호를 파라미터 값으로 재설정
# 나머지 비밀(PG_EXPORTER_*)은 같은 폴더의 .env 에서 compose 가 읽는다.
#
# 스택은 admin 서버(moyeota-admin-backend, 172.16.1.17)에서 돈다. 예전에는 k6 서버에 있어서 그 서버를 끄면 운영 지표·로그도 같이 끊겼다.
# admin 서버의 IAM 역할은 /moyeota-admin 아래 파라미터와 S3 의 admin-backend/ 만 읽을 수 있어서 비밀번호와 파일을 그 아래에 둔다.
# 서버에 올리기:
#   (내 PC)   aws s3 sync infra/monitoring s3://moyeota-deploy-bucket/admin-backend/monitoring --delete --exclude '.env'
#   (admin)   aws s3 sync s3://moyeota-deploy-bucket/admin-backend/monitoring /opt/monitoring && chmod +x /opt/monitoring/up.sh && /opt/monitoring/up.sh
#   설정만 바꿨으면: docker compose restart prometheus (또는 loki, tempo)
set -euo pipefail
cd "$(dirname "$0")"
command -v aws >/dev/null || { echo "aws CLI 가 없습니다. 한 번만 설치: apt-get install -y unzip && curl -sS https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip && unzip -qo /tmp/awscliv2.zip -d /tmp && /tmp/aws/install" >&2; exit 1; }
export GRAFANA_PASSWORD
GRAFANA_PASSWORD=$(aws ssm get-parameter --region ap-northeast-2 \
  --name /moyeota-admin/monitoring/GRAFANA_PASSWORD --with-decryption \
  --query Parameter.Value --output text)
if [ "${1:-}" = "reset-password" ]; then
  # 이미 떠 있는 Grafana 는 환경변수만으로 비밀번호가 안 바뀐다(최초 기동 때만 적용). 파라미터 값으로 맞춘다
  docker compose exec grafana grafana cli admin reset-admin-password "$GRAFANA_PASSWORD"
  exit 0
fi
docker compose up -d "$@"
