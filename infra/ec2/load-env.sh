#!/bin/bash
# 파라미터 스토어에서 환경 변수를 받아 /etc/moyeota/moyeota.env 를 만든다. CodeDeploy AfterInstall 과 bootstrap 이 부른다.
# 어느 환경(dev/prod)인지는 EC2 태그 Env 로 정한다 - CodeDeploy 배포 그룹이 보는 태그와 같은 것.
#   태그는 인스턴스 메타데이터(IMDSv2)로 읽는다. 인스턴스 설정에서 "메타데이터의 태그 허용" 이 켜져 있어야 한다.
#   태그를 못 읽거나 dev/prod 가 아니면 멈춘다 - 추측해서 prod 파라미터를 읽는 일이 없게.
set -euo pipefail
REGION=ap-northeast-2
ENV_FILE=/etc/moyeota/moyeota.env
FCM_FILE=/etc/moyeota/fcm-account.json
IMDS=http://169.254.169.254/latest

TOKEN=$(curl -sf -X PUT "$IMDS/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
ENV=$(curl -sf -H "X-aws-ec2-metadata-token: $TOKEN" "$IMDS/meta-data/tags/instance/Env" || true)
case "$ENV" in
  dev|prod) ;;
  *) echo "EC2 태그 Env 를 읽지 못했습니다(값: '$ENV'). 인스턴스에 Env=dev|prod 태그와 '메타데이터의 태그 허용' 이 필요합니다" >&2; exit 1 ;;
esac
PREFIX=/moyeota/$ENV

aws ssm get-parameters-by-path --region "$REGION" --path "$PREFIX" \
  --with-decryption --output json \
| python3 -c "
import json, sys
prefix = '$PREFIX/'
params = json.load(sys.stdin)['Parameters']
env = []
for p in params:
    key = p['Name'][len(prefix):]
    if key == 'FCM_SERVICE_ACCOUNT_JSON':
        open('$FCM_FILE', 'w').write(p['Value'])
    else:
        env.append(f'{key}={p[\"Value\"]}')
env.append('FCM_SERVICE_ACCOUNT_PATH=$FCM_FILE')
env.append('SPRING_PROFILES_ACTIVE=$ENV')
open('$ENV_FILE', 'w').write('\n'.join(env) + '\n')
print(f'[$ENV] {len(params)} params loaded from $PREFIX')
"
chmod 600 "$ENV_FILE"
[ -f "$FCM_FILE" ] && chmod 600 "$FCM_FILE"
