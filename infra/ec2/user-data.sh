#!/bin/bash
set -euo pipefail
apt-get install -y -q unzip
curl -sS https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o /tmp/awscliv2.zip
unzip -qo /tmp/awscliv2.zip -d /tmp && /tmp/aws/install --update
aws s3 cp s3://moyeota-deploy-bucket/ec2/bootstrap.sh /tmp/bootstrap.sh
bash /tmp/bootstrap.sh