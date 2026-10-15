"""포트원 웹훅 키 확인용 로컬 서버. 결제 처리·저장 없이 서명만 검증한다.

Standard Webhooks: HMAC-SHA256(id.timestamp.원본본문), 허용 시간차 5분.
https://developers.portone.io/opi/ko/integration/webhook/readme-v2
운영 서버용 구현이 아니며 Spring 설정 바인딩을 검증하지 않는다.
"""
import argparse
import base64
import binascii
import hashlib
import hmac
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import subprocess
import time


def decode_secret(value):
    encoded = value.removeprefix("whsec_")
    try:
        key = base64.b64decode(encoded + "=" * (-len(encoded) % 4), validate=True)
    except (ValueError, binascii.Error):
        raise ValueError("웹훅 시크릿의 Base64 형식을 확인하세요.") from None
    if not key:
        raise ValueError("웹훅 시크릿이 비어 있습니다.")
    return key


def verify(key, body, headers, now=None):
    headers = {name.lower(): value for name, value in headers.items()}
    message_id = headers.get("webhook-id", "")
    timestamp = headers.get("webhook-timestamp", "")
    signatures = headers.get("webhook-signature", "")
    if not message_id or not re.fullmatch(r"[0-9]{1,12}", timestamp) or not signatures:
        return False
    if abs((time.time() if now is None else now) - int(timestamp)) > 300:
        return False
    signed = f"{message_id}.{timestamp}.".encode("utf-8") + body
    expected = hmac.digest(key, signed, hashlib.sha256)
    for candidate in signatures.split():
        version, separator, signature = candidate.partition(",")
        if version != "v1" or not separator:
            continue
        try:
            received = base64.b64decode(signature, validate=True)
        except (ValueError, binascii.Error):
            continue
        if hmac.compare_digest(expected, received):
            return True
    return False


def handler_for(key):
    class Handler(BaseHTTPRequestHandler):
        # 헤더, 본문, 시크릿을 로그에 출력하지 않는다.
        def log_message(self, format, *args):
            pass

        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def reply(self, status, result):
            body = json.dumps(result).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == "/health":
                self.reply(200, {"listening": True, "secretVerified": False})
            else:
                self.reply(404, {"error": "NOT_FOUND"})

        def do_POST(self):
            if self.path != "/webhooks/portone":
                self.reply(404, {"error": "NOT_FOUND"})
                return
            # 중복 헤더·chunked 전송 등 모호한 요청은 테스트 도구에서 거부한다.
            required = ("webhook-id", "webhook-timestamp", "webhook-signature")
            if any(len(self.headers.get_all(name, [])) != 1 for name in required):
                self.reply(401, {"verified": False, "error": "SIGNATURE_HEADERS"})
                print("웹훅 검증 실패: 서명 헤더 누락 또는 중복", flush=True)
                return
            lengths = self.headers.get_all("Content-Length", [])
            if self.headers.get("Transfer-Encoding") or len(lengths) != 1:
                self.reply(400, {"error": "CONTENT_LENGTH_REQUIRED"})
                return
            try:
                size = int(lengths[0])
                if not 0 <= size <= 1024 * 1024:
                    self.reply(413, {"error": "BODY_SIZE"})
                    return
                body = self.rfile.read(size)
                if len(body) != size:
                    self.reply(400, {"error": "INCOMPLETE_BODY"})
                    return
            except (ValueError, OSError):
                self.reply(400, {"error": "INVALID_BODY"})
                return
            valid = verify(key, body, self.headers)
            self.reply(200 if valid else 401, {"verified": valid})
            print("웹훅 서명 검증 성공" if valid else
                  "웹훅 검증 실패: 키·원본 본문·서명·요청 시각을 확인하세요.", flush=True)

    return Handler


def main():
    parser = argparse.ArgumentParser(description="포트원 웹훅 서명 검증용 로컬 수신기")
    parser.add_argument("--port", type=int, default=8089)
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("포트는 1~65535 범위여야 합니다.")
    try:
        result = subprocess.run(
            ["ruby", str(Path(__file__).with_name("portone-config.rb")), "webhook-secret"],
            capture_output=True, text=True, timeout=10, check=True,
        )
        key = decode_secret(result.stdout.strip())
    except (OSError, subprocess.SubprocessError, ValueError):
        parser.exit(1, "웹훅 설정 로드 실패: Ruby 설치 및 로컬 웹훅 시크릿을 확인하세요.\n")
    try:
        with ThreadingHTTPServer(("127.0.0.1", args.port), handler_for(key)) as server:
            print(f"수신 대기: http://127.0.0.1:{args.port}/webhooks/portone", flush=True)
            print("아직 키 검증 전입니다. HTTPS 터널을 연결한 후 포트원에서 호출 테스트를 보내세요.", flush=True)
            print("종료: Ctrl+C / 키 변경 시 재시작", flush=True)
            server.serve_forever()
    except KeyboardInterrupt:
        print("\n웹훅 테스트 종료")
    except OSError:
        parser.exit(1, "서버 시작 또는 통신 실패: 포트 사용 여부를 확인하세요.\n")


if __name__ == "__main__":
    main()
