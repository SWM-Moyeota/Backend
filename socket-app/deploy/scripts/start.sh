#!/bin/bash
# 새 이미지를 먼저 받아 두고 옛 컨테이너를 내림 - 받는 동안에는 기존 연결이 그대로 유지됨
# ApplicationStop 훅을 쓰지 않는 까닭: CodeDeploy 는 ApplicationStop 을 다운로드보다 먼저 돌려서, 거기서 내리면 이미지를 받는 시간만큼 더 끊김
set -euo pipefail
REGION=ap-northeast-2
NAME=moyeota-socket
HOME_DIR=/opt/moyeota-socket

# image.env 는 이번 배포 번들에 들어 있음 (IMAGE=<ECR 주소>/moyeota-socket:<커밋 해시>)
source "$HOME_DIR/image.env"

# 이 번들의 load-env.sh 로 환경 변수를 다시 만듦 - 파라미터를 바꾼 뒤 재배포만 하면 반영됨
"$(dirname "$0")/load-env.sh"

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${IMAGE%%/*}"
docker pull "$IMAGE"

# 앱이 연결을 1001 로 닫고 남은 이벤트를 처리하도록 40초까지 기다림 - Spring 종료 단계 기본 30초보다 길게
docker stop --time 40 "$NAME" 2>/dev/null || true
docker rm "$NAME" 2>/dev/null || true

docker run -d --name "$NAME" \
  --restart unless-stopped \
  --stop-timeout 40 \
  --env-file /etc/moyeota-socket/socket.env \
  -p 8081:8081 -p 9091:9091 \
  --log-driver journald --log-opt tag="$NAME" \
  "$IMAGE"

# 3일 넘게 안 쓴 이미지를 지워 디스크가 차지 않게 함 - 지금 돌고 있는 이미지는 지워지지 않음
docker image prune -af --filter "until=72h" >/dev/null || true
