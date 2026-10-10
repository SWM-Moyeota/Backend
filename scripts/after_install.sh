#!/bin/bash
set -e
# load-env.sh 는 저장소(infra/ec2)가 원본이고 deploy.yml 이 배포 직전에 S3 로 동기화한다.
# 서버 사본은 bootstrap 때 받은 것이라, 배포마다 새로 받아야 스크립트 변경이 반영된다.
aws s3 cp s3://moyeota-deploy-bucket/ec2/load-env.sh /opt/moyeota/bin/load-env.sh --region ap-northeast-2
chmod +x /opt/moyeota/bin/load-env.sh
/opt/moyeota/bin/load-env.sh
