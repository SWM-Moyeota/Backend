#!/usr/bin/env bash
# 우리 서버의 JWT를 직접 만들어주는 개발용 도구 (Git Bash에서 실행)
#
# 왜 필요한가
#   구글 로그인을 거치지 않고도 JWTFilter / 인가 동작을 확인하기 위해서다.
#   서명 키(spring.jwt.secret)를 우리가 알고 있으므로, 서버가 발급한 것과
#   구별할 수 없는 토큰을 그대로 만들 수 있다. HS256은 대칭키라서 가능한 일이다.
#   (= 이 secret이 새면 누구나 아무 사용자로 로그인할 수 있다는 뜻이기도 하다)
#
# 사용법
#   ./mint-jwt.sh <category> [만료까지_초] [username] [role]
#
#   ./mint-jwt.sh access                 # 10분짜리 access
#   ./mint-jwt.sh refresh                # 10분짜리 refresh (category만 다름)
#   ./mint-jwt.sh access -60             # 60초 전에 이미 만료된 access  <- 만료 테스트용
#   ./mint-jwt.sh access 600 "google 123" ROLE_ADMIN
#
# 참고: 여기서 만든 refresh로는 /api/auth/reissue가 통과하지 않는다.
#       서버가 DB(RefreshEntity)에 저장된 값인지 대조하기 때문이다. 그게 정상 동작이다.

set -euo pipefail

CATEGORY="${1:-access}"
TTL="${2:-600}"
USERNAME="${3:-google 000000000000000000000}"
ROLE="${4:-ROLE_USER}"

# 서명 키는 저장소에 올리지 않는 application-local.properties 에 있다.
# (예전에는 application.properties 에 있었지만, 그대로 두면 커밋과 함께 새어나간다)
PROPS="$(dirname "$0")/../src/main/resources/application-local.properties"
SECRET="$(grep '^spring.jwt.secret=' "$PROPS" 2>/dev/null | cut -d= -f2-)"

if [ -z "$SECRET" ]; then
  echo "spring.jwt.secret 을 $PROPS 에서 찾지 못했다" >&2
  echo "application-local.properties.example 을 복사해 값을 채웠는지 확인할 것" >&2
  exit 1
fi

# JWT는 base64url(패딩 없음)을 쓴다. 일반 base64와 다른 점은 +/ -> -_ 와 = 제거뿐이다.
b64url() { base64 -w0 | tr '+/' '-_' | tr -d '='; }

NOW=$(date +%s)
EXP=$((NOW + TTL))

# JJWT의 signWith(SecretKey)는 키 길이를 보고 HS256을 고른다. header도 그에 맞춘다.
HEADER=$(printf '{"alg":"HS256"}' | b64url)

# 클레임 구성은 JWTUtil.createJwt() 와 똑같이 맞춘다.
# iat/exp는 JWT 표준상 "초" 단위다. (JJWT가 밀리초 Date를 초로 바꿔 넣는다)
PAYLOAD=$(printf '{"category":"%s","username":"%s","role":"%s","iat":%d,"exp":%d}' \
  "$CATEGORY" "$USERNAME" "$ROLE" "$NOW" "$EXP" | b64url)

SIGNING_INPUT="${HEADER}.${PAYLOAD}"

SIGNATURE=$(printf '%s' "$SIGNING_INPUT" \
  | openssl dgst -sha256 -hmac "$SECRET" -binary \
  | b64url)

echo "${SIGNING_INPUT}.${SIGNATURE}"
