#!/bin/bash
# 운영 EC2 준비 스크립트. user-data.sh(stub)가 S3에서 받아 실행한다. 여러 번 실행해도 안전하다.
# jar 배포는 CodeDeploy가 한다. 여기서는 jar 이외의 모든 것을 준비한다.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
REGION=ap-northeast-2
S3=s3://moyeota-deploy-bucket/ec2
OTEL_AGENT_VERSION=2.31.1                 # 나온 지 얼마 안 된 릴리스는 바로 올리지 않는다. 올릴 때는 SHA256 도 같이 바꾼다
OTEL_AGENT_SHA256=bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba   # GitHub 릴리스의 opentelemetry-javaagent.jar digest

# 1. 패키지 (나중에 AMI로 옮길 부분)
mkdir -p /etc/apt/keyrings
wget -qO- https://apt.grafana.com/gpg.key | gpg --dearmor > /etc/apt/keyrings/grafana.gpg
echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" > /etc/apt/sources.list.d/grafana.list
apt-get update -q
# Redis 는 서버 안에 두지 않는다 - ElastiCache(REDIS_HOST)를 쓴다. 서버가 여러 대일 때 SSE 신호·채팅·캐시가 한 곳을 봐야 한다
apt-get install -y -q openjdk-25-jre-headless prometheus-node-exporter prometheus-redis-exporter alloy ruby unzip
usermod -aG systemd-journal alloy

# 2. AWS CLI v2 (stub이 깔지만, 기존 서버에서 직접 돌릴 때를 위해 확인)
if ! command -v aws >/dev/null; then
  curl -sS https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip
  unzip -qo /tmp/awscliv2.zip -d /tmp && /tmp/aws/install --update
fi

# 3. CodeDeploy 에이전트
if ! systemctl is-active -q codedeploy-agent; then
  wget -q "https://aws-codedeploy-$REGION.s3.$REGION.amazonaws.com/latest/install" -O /tmp/codedeploy-install
  chmod +x /tmp/codedeploy-install && /tmp/codedeploy-install auto
fi

# 4. OpenTelemetry Java 에이전트 (추적). jar 는 20MB 라 저장소에 두지 않고 버전을 고정해 받는다.
#    유닛 파일은 버전 없는 경로(심볼릭 링크)를 가리킨다 - 버전을 올릴 때 이 파일의 숫자만 바꾸면 된다.
mkdir -p /opt/otel
if [ ! -s "/opt/otel/opentelemetry-javaagent-$OTEL_AGENT_VERSION.jar" ]; then
  curl -fsSL -o "/opt/otel/opentelemetry-javaagent-$OTEL_AGENT_VERSION.jar.tmp" \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$OTEL_AGENT_VERSION/opentelemetry-javaagent.jar"
  # 운영 JVM 에 주입되는 코드다 - 받은 파일이 릴리스의 그 파일인지 확인하고, 다르면 여기서 멈춘다
  echo "$OTEL_AGENT_SHA256  /opt/otel/opentelemetry-javaagent-$OTEL_AGENT_VERSION.jar.tmp" | sha256sum -c -
  mv "/opt/otel/opentelemetry-javaagent-$OTEL_AGENT_VERSION.jar.tmp" "/opt/otel/opentelemetry-javaagent-$OTEL_AGENT_VERSION.jar"
fi
ln -sfn "/opt/otel/opentelemetry-javaagent-$OTEL_AGENT_VERSION.jar" /opt/otel/opentelemetry-javaagent.jar

# 5. 설정 파일 (저장소 infra/ec2/ → S3 동기화본)
mkdir -p /opt/moyeota/bin /etc/moyeota
aws s3 cp "$S3/moyeota.service"    /etc/systemd/system/moyeota.service
aws s3 cp "$S3/load-env.sh"        /opt/moyeota/bin/load-env.sh && chmod +x /opt/moyeota/bin/load-env.sh
aws s3 cp "$S3/otel.env"           /etc/moyeota/otel.env           # 비밀값 없음 - 에이전트 설정
aws s3 cp "$S3/alloy/config.alloy" /etc/alloy/config.alloy

# 6. Parameter Store → env. 앱은 전체, Alloy는 필요한 값만 (비밀값을 Alloy에 넘기지 않는다)
/opt/moyeota/bin/load-env.sh
grep -E '^(LOKI_URL|DEPLOY_ENV|TEMPO_URL)=' /etc/moyeota/moyeota.env > /etc/alloy/env
# Redis 지표 수집기는 ElastiCache 를 본다. 전송 중 암호화가 켜져 있어 rediss:// 로 붙는다 (인증서는 Amazon CA, 시스템 신뢰 저장소로 검증)
REDIS_HOST=$(grep -E '^REDIS_HOST=' /etc/moyeota/moyeota.env | cut -d= -f2- | tr -d '"')
printf 'ARGS="--redis.addr=rediss://%s:6379"\n' "$REDIS_HOST" > /etc/default/prometheus-redis-exporter
chown root:alloy /etc/alloy/env && chmod 640 /etc/alloy/env
mkdir -p /etc/systemd/system/alloy.service.d
printf '[Service]\nEnvironmentFile=/etc/alloy/env\n' > /etc/systemd/system/alloy.service.d/env.conf

# 7. 기동
systemctl daemon-reload
systemctl enable --now prometheus-node-exporter prometheus-redis-exporter
systemctl restart prometheus-redis-exporter   # 바뀐 주소를 읽게
systemctl enable moyeota                 # jar는 CodeDeploy가 놓고 시작한다 (이미 떠 있는 앱은 여기서 재시작하지 않는다)
systemctl enable alloy && systemctl restart alloy