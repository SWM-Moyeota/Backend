"""실제 포트원 키 없이 실행: python3 -m unittest discover -s http -p 'test_portone_webhook.py'"""
import base64
import hashlib
import hmac
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import threading
import time
import unittest

spec = importlib.util.spec_from_file_location(
    "webhook_check", Path(__file__).with_name("portone-webhook-check.py"))
webhook = importlib.util.module_from_spec(spec)
spec.loader.exec_module(webhook)


class SignatureTest(unittest.TestCase):
    def setUp(self):
        self.key = b"local-test-secret-only"
        self.body = b'{"type":"TEST"}'
        self.timestamp = 1700000000
        self.headers = {
            "Webhook-Id": "fixture-id",
            "Webhook-Timestamp": str(self.timestamp),
            "Webhook-Signature": "v1,kdctIXXGcM2dS25jofSewjK86R/VO85O881mtVa2r8w=",
        }

    def test_정상_서명과_키_접두사(self):
        key = webhook.decode_secret("whsec_" + base64.b64encode(self.key).decode())
        self.assertTrue(webhook.verify(key, self.body, self.headers, self.timestamp))

    def test_변조된_본문과_다른_키_거부(self):
        self.assertFalse(webhook.verify(self.key, self.body + b" ", self.headers, self.timestamp))
        self.assertFalse(webhook.verify(b"wrong-key", self.body, self.headers, self.timestamp))

    def test_오래되거나_미래인_요청_거부(self):
        for difference in (-301, 301):
            self.assertFalse(webhook.verify(
                self.key, self.body, self.headers, self.timestamp + difference))

    def test_키_교체_중_여러_서명과_잘못된_Base64(self):
        self.headers["Webhook-Signature"] = "v1,%%% v2,ignored " + self.headers["Webhook-Signature"]
        self.assertTrue(webhook.verify(self.key, self.body, self.headers, self.timestamp))
        self.headers["Webhook-Signature"] = "v1,%%%"
        self.assertFalse(webhook.verify(self.key, self.body, self.headers, self.timestamp))

    def test_필수_헤더_누락과_잘못된_시각(self):
        for name in self.headers:
            headers = {k: v for k, v in self.headers.items() if k != name}
            self.assertFalse(webhook.verify(self.key, self.body, headers, self.timestamp))
        self.headers["Webhook-Timestamp"] = "nan"
        self.assertFalse(webhook.verify(self.key, self.body, self.headers, self.timestamp))

    def test_잘못된_키_설정_거부(self):
        for value in ("", "whsec_", "whsec_%%%"):
            with self.assertRaises(ValueError):
                webhook.decode_secret(value)


class ReceiverTest(unittest.TestCase):
    def test_HTTP_수신_서명검증과_누락거부(self):
        key = b"local-test-secret-only"
        server = ThreadingHTTPServer(("127.0.0.1", 0), webhook.handler_for(key))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            connection = HTTPConnection("127.0.0.1", server.server_port, timeout=5)
            connection.request("GET", "/health")
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertFalse(json.loads(response.read())["secretVerified"])
            connection.request("POST", "/webhooks/portone", b"{}")
            response = connection.getresponse()
            self.assertEqual(response.status, 401)
            self.assertFalse(json.loads(response.read())["verified"])
            timestamp = str(int(time.time()))
            signature = base64.b64encode(hmac.new(
                key, f"test-id.{timestamp}.{{}}".encode(), hashlib.sha256).digest()).decode()
            connection.request("POST", "/webhooks/portone", b"{}", {
                "webhook-id": "test-id", "webhook-timestamp": timestamp,
                "webhook-signature": "v1," + signature,
            })
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertTrue(json.loads(response.read())["verified"])
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
