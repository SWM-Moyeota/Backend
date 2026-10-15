#!/usr/bin/env bash
# 로컬 설정의 웹훅 키로 포트원 테스트 알림을 검증합니다.
set -euo pipefail
set +x
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$script_dir/portone-webhook-check.py" "$@"
