#!/bin/bash
# 운영 EC2 준비 스크립트. user-data.sh(stub)가 S3에서 받아 실행한다. 여러 번 실행해도 안전하다.
# jar 배포는 CodeDeploy가 한다. 여기서는 jar 이외의 모든 것을 준비한다.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
REGION=ap-northeast-2
S3=s3://moyeota-deploy-bucket/ec2

# 1. 패키지 (나중에 AMI로 옮길 부분)
mkdir -p /etc/apt/keyrings
wget -qO- https://apt.grafana.com/gpg.key | gpg --dearmor > /etc/apt/keyrings/grafana.gpg
echo "deb [signed-by=/etc/apt/keyrings/grafana.gpg] https://apt.grafana.com stable main" > /etc/apt/sources.list.d/grafana.list
apt-get update -q
apt-get install -y -q openjdk-25-jre-headless redis-server prometheus-node-exporter prometheus-redis-exporter alloy ruby unzip
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

# 4. 설정 파일 (저장소 infra/ec2/ → S3 동기화본)
mkdir -p /opt/moyeota/bin /etc/moyeota
aws s3 cp "$S3/moyeota.service"    /etc/systemd/system/moyeota.service
aws s3 cp "$S3/load-env.sh"        /opt/moyeota/bin/load-env.sh && chmod +x /opt/moyeota/bin/load-env.sh
aws s3 cp "$S3/alloy/config.alloy" /etc/alloy/config.alloy

# 5. Parameter Store → env. 앱은 전체, Alloy는 두 값만 (비밀값을 Alloy에 넘기지 않는다)
/opt/moyeota/bin/load-env.sh
grep -E '^(LOKI_URL|DEPLOY_ENV)=' /etc/moyeota/moyeota.env > /etc/alloy/env
chown root:alloy /etc/alloy/env && chmod 640 /etc/alloy/env
mkdir -p /etc/systemd/system/alloy.service.d
printf '[Service]\nEnvironmentFile=/etc/alloy/env\n' > /etc/systemd/system/alloy.service.d/env.conf

# 6. 기동
systemctl daemon-reload
systemctl enable --now redis-server prometheus-node-exporter prometheus-redis-exporter
systemctl enable moyeota                 # jar는 CodeDeploy가 놓고 시작한다
systemctl enable alloy && systemctl restart alloy