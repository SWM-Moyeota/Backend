"""로컬 카드 등록 화면과 동일 출처 API 프록시. 시크릿 설정은 읽지 않는다."""
import argparse
import http.client
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
FILES = {"/": ("index.html", "text/html; charset=utf-8"),
         "/app.js": ("app.js", "text/javascript; charset=utf-8")}
API = {("GET", "/api/v1/local/users/billing-customer"), ("POST", "/api/v1/payments/methods")}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass  # 리디렉션 URL의 빌링키나 인증 정보를 로그에 남기지 않는다.

    def respond(self, code, body, content_type="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.handle_request()

    def do_POST(self):
        self.handle_request()

    def handle_request(self):
        expected_host = f"127.0.0.1:{self.server.server_port}"
        if self.headers.get("Host") != expected_host:
            self.respond(403, b'{}')
            return
        path = urlsplit(self.path).path
        if self.command == "GET" and path in FILES:
            name, content_type = FILES[path]
            self.respond(200, (ROOT / name).read_bytes(), content_type)
            return
        if (self.command, path) not in API:
            self.respond(404, b'{}')
            return
        origin = self.headers.get("Origin")
        if origin and origin != f"http://{expected_host}":
            self.respond(403, b'{}')
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 <= length <= 4096:
                self.respond(413, b'{}')
                return
            body = self.rfile.read(length) if length else None
            connection = http.client.HTTPConnection("127.0.0.1", self.server.backend_port, timeout=65)
            try:
                connection.request(self.command, path, body=body, headers={
                    "Authorization": self.headers.get("Authorization", ""),
                    "Content-Type": "application/json", "Accept": "application/json"})
                response = connection.getresponse()
                self.respond(response.status, response.read(), response.getheader("Content-Type", "application/json"))
            finally:
                connection.close()
        except (OSError, ValueError, http.client.HTTPException):
            self.respond(502, json.dumps({"message": "로컬 백엔드에 연결하지 못했습니다."}).encode())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--backend-port", type=int, default=8080)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.backend_port = args.backend_port
    print(f"카드 등록 화면: http://127.0.0.1:{args.port} (백엔드: {args.backend_port})", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
