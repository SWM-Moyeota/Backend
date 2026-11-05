#!/bin/bash
# 소켓 서버에 필요한 파라미터만 받아 /etc/moyeota-socket/socket.env 를 만듦. start.sh 가 배포마다 부름
# API 의 load-env.sh 와 달리 /moyeota/{env} 전체를 읽지 않음 - DB·결제·JWT 키를 소켓 서버에 두지 않기 위함
# 어느 환경인지는 EC2 태그 SocketEnv 로 정함 - Env 태그를 쓰면 API 배포 그룹(Env=dev)이 이 서버에도 API 를 배포함
#   태그를 못 읽거나 dev/prod 가 아니면 멈춤, 파라미터가 하나라도 없으면 멈춤
set -euo pipefail
REGION=ap-northeast-2
ENV_FILE=/etc/moyeota-socket/socket.env
NAMES=(REDIS_HOST REDIS_SSL_ENABLED WS_ALLOWED_ORIGINS CHAT_API_BASE_URL INTERNAL_API_TOKEN)
IMDS=http://169.254.169.254/latest

TOKEN=$(curl -sf -X PUT "$IMDS/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
ENV=$(curl -sf -H "X-aws-ec2-metadata-token: $TOKEN" "$IMDS/meta-data/tags/instance/SocketEnv" || true)
case "$ENV" in
  dev|prod) ;;
  *) echo "EC2 태그 SocketEnv 를 읽지 못했습니다(값: '$ENV'). SocketEnv=dev|prod 태그와 '메타데이터의 태그 허용' 이 필요합니다" >&2; exit 1 ;;
esac
PREFIX=/moyeota/$ENV

mkdir -p "$(dirname "$ENV_FILE")"
# shellcheck disable=SC2046
aws ssm get-parameters --region "$REGION" --with-decryption --output json \
  --names $(printf "$PREFIX/%s " "${NAMES[@]}") \
| python3 -c "
import json, sys
resp = json.load(sys.stdin)
missing = resp.get('InvalidParameters', [])
if missing:
    sys.exit('[$ENV] 파라미터가 없습니다: ' + ', '.join(missing))
lines = [p['Name'][len('$PREFIX/'):] + '=' + p['Value'] for p in resp['Parameters']]
lines.append('SPRING_PROFILES_ACTIVE=$ENV')
open('$ENV_FILE', 'w').write('\n'.join(lines) + '\n')
print('[$ENV] ' + str(len(resp['Parameters'])) + ' params loaded')
"
chmod 600 "$ENV_FILE"
