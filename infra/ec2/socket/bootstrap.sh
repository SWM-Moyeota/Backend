#!/bin/bash
# 소켓 EC2 준비 스크립트. EC2 를 만들 때 이 파일 내용을 그대로 user-data 에 넣음. 여러 번 실행해도 안전함
# 컨테이너 배포는 CodeDeploy 가 함(socket-app/deploy). 여기서는 Docker, CodeDeploy 에이전트, 지표 수집기만 준비함
# Java 는 이미지 안에 있어 깔지 않음. 이미지가 amd64 라 x86 인스턴스(t3 등)에서만 돎
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
REGION=ap-northeast-2

# 1. 패키지
apt-get update -q
apt-get install -y -q docker.io prometheus-node-exporter ruby unzip python3 curl
systemctl enable --now docker prometheus-node-exporter

# 2. AWS CLI v2 - start.sh 가 ECR 로그인과 파라미터 읽기에 씀
if ! command -v aws >/dev/null; then
  curl -sS https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip
  unzip -qo /tmp/awscliv2.zip -d /tmp && /tmp/aws/install --update
fi

# 3. CodeDeploy 에이전트 - infra/ec2/bootstrap.sh 와 같은 방법
#    공식 설치 스크립트는 Ubuntu 26.04 의 Ruby 3.3 을 거부하고 deb 의 Depends 도 ruby3.2 까지라, Depends 를 설치된 ruby 로 고쳐 설치함
if ! systemctl is-active -q codedeploy-agent; then
  CD_BUCKET="https://aws-codedeploy-$REGION.s3.$REGION.amazonaws.com"
  CD_DEB=$(curl -fsSL "$CD_BUCKET/latest/LATEST_VERSION" | python3 -c 'import json,sys; print(json.load(sys.stdin)["deb"])')
  RUBY_PKG=$(dpkg-query -W -f='${Package}\n' 'ruby3.*' 2>/dev/null | grep -E '^ruby3\.[0-9]+$' | head -n1)
  curl -fsSL -o /tmp/codedeploy-agent.deb "$CD_BUCKET/$CD_DEB"
  rm -rf /tmp/codedeploy-pkg && dpkg-deb -R /tmp/codedeploy-agent.deb /tmp/codedeploy-pkg
  sed -i "s/^Depends: .*/Depends: $RUBY_PKG/" /tmp/codedeploy-pkg/DEBIAN/control
  dpkg-deb -b /tmp/codedeploy-pkg /tmp/codedeploy-agent-fixed.deb
  dpkg -i /tmp/codedeploy-agent-fixed.deb
  systemctl enable --now codedeploy-agent
fi

# 4. 배포 폴더 - appspec 이 image.env 를 여기에 놓음
mkdir -p /opt/moyeota-socket /etc/moyeota-socket
