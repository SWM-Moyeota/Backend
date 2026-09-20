#!/bin/sh
# Sentinel 하나를 지정한 포트로 띄운다. Sentinel 은 설정 파일을 런타임에 고쳐 쓰므로 컨테이너 안에서 생성한다.
# HOST_IP: 노드와 Sentinel 이 서로를 부르는 주소(호스트 LAN IP). up.sh 가 넣어 준다.
set -eu
PORT="$1"
cat > /data/sentinel.conf <<EOF
port ${PORT}
bind 0.0.0.0
protected-mode no
sentinel announce-ip ${HOST_IP}
sentinel announce-port ${PORT}
sentinel monitor moyeota ${HOST_IP} 6381 2
sentinel down-after-milliseconds moyeota 2000
sentinel failover-timeout moyeota 10000
sentinel parallel-syncs moyeota 1
EOF
exec redis-server /data/sentinel.conf --sentinel
