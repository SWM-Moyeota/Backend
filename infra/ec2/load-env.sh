#!/bin/bash
set -euo pipefail
REGION=ap-northeast-2
PREFIX=/moyeota/prod
ENV_FILE=/etc/moyeota/moyeota.env
FCM_FILE=/etc/moyeota/fcm-account.json

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
env.append('SPRING_PROFILES_ACTIVE=prod')
open('$ENV_FILE', 'w').write('\n'.join(env) + '\n')
print(f'{len(params)} params loaded')
"
chmod 600 "$ENV_FILE"
[ -f "$FCM_FILE" ] && chmod 600 "$FCM_FILE"

