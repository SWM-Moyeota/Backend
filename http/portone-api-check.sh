#!/usr/bin/env bash
# API 키 인증을 조회 요청으로 확인합니다. 결제 생성·변경은 하지 않습니다.
set -euo pipefail
set +x

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == '--prepare-http' ]]; then
  exec ruby "$script_dir/portone-config.rb" prepare-http
elif [[ $# -ne 0 ]]; then
  printf '사용법: bash http/portone-api-check.sh [--prepare-http]\n' >&2
  exit 1
fi

# 인증 헤더를 표준 입력으로 전달해 curl 프로세스 인자에 키를 노출하지 않습니다.
# 먼저 설정을 검증합니다. 실패하면 curl을 실행하지 않습니다.
ruby "$script_dir/portone-config.rb" check
ruby "$script_dir/portone-config.rb" header |
  curl --disable --silent --show-error --fail-with-body \
    --connect-timeout 10 --max-time 30 \
    --header @- --header 'Accept: application/json' \
    --get 'https://api.portone.io/payments' \
    --data-urlencode 'requestBody={"page":{"number":0,"size":1}}' \
    --write-out '\nHTTP 상태: %{http_code}\n'
