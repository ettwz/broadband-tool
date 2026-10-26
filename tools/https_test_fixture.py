#!/usr/bin/env python3
"""Local WebView TLS fixture. Ephemeral private key stays in memory only.

Writes a public test certificate and a debug-only trust resource, which must be
removed after the integration run. Never installs a device-wide trust anchor.
Requires pyOpenSSL; exposes only loopback ports for adb reverse.
"""
import json
import socket
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from OpenSSL import SSL, crypto

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "apps/mobile/android/app/src/debug/res"
PUBLIC = RES / "raw/web_fixture.crt"
CONFIG = RES / "xml/network_security_config.xml"
counts = {"page": 0, "worker": 0, "tick": 0, "connections": 0}
lock = threading.Lock()


def serve(connection):
    tls = SSL.Connection(context, connection)
    try:
        tls.set_accept_state()
        tls.do_handshake()
        request = b""
        while b"\r\n\r\n" not in request and len(request) < 8192:
            request += tls.recv(4096)
        path = request.split(b" ")[1].split(b"?")[0]
        with lock:
            counts["connections"] += 1
            counts["worker" if path == b"/worker.js" else "tick" if path == b"/tick" else "page"] += 1
        kind = "text/plain"
        if path == b"/worker.js":
            kind = "application/javascript"
            body = b"setInterval(()=>fetch('/tick?worker='+Date.now()).then(()=>postMessage('ok')).catch(()=>{}),250);"
        elif path == b"/tick":
            body = b"ok"
        else:
            kind = "text/html"
            body = b"""<!doctype html><title>TLS fixture</title><p>TLS page ready</p><script>
const worker=new Worker('/worker.js'); worker.onmessage=()=>document.title='Worker ready';
setInterval(()=>Promise.all([fetch('/tick?a='+Date.now()),fetch('/tick?b='+Date.now())]).catch(()=>{}),250);
</script>"""
        header = f"HTTP/1.1 200 OK\r\nContent-Type: {kind}\r\nContent-Length: {len(body)}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".encode()
        tls.sendall(header + body)
    except (SSL.Error, OSError, IndexError):
        pass
    finally:
        try:
            tls.shutdown()
        except (SSL.Error, OSError):
            pass
        tls.close()


class Stats(BaseHTTPRequestHandler):
    def do_GET(self):
        with lock:
            payload = json.dumps(counts).encode()
        self.send_response(200)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_):
        pass


key = crypto.PKey()
key.generate_key(crypto.TYPE_RSA, 2048)
cert = crypto.X509()
cert.set_version(2)
cert.set_serial_number(1)
cert.get_subject().CN = "Temporary local WebView fixture"
cert.gmtime_adj_notBefore(-60)
cert.gmtime_adj_notAfter(86400)
cert.set_issuer(cert.get_subject())
cert.set_pubkey(key)
cert.add_extensions([
    crypto.X509Extension(b"basicConstraints", True, b"CA:TRUE"),
    crypto.X509Extension(b"subjectAltName", False, b"IP:127.0.0.1,DNS:localhost"),
])
cert.sign(key, "sha256")
context = SSL.Context(SSL.TLS_SERVER_METHOD)
context.use_privatekey(key)
context.use_certificate(cert)
PUBLIC.parent.mkdir(parents=True, exist_ok=True)
CONFIG.parent.mkdir(parents=True, exist_ok=True)
if CONFIG.exists() or PUBLIC.exists():
    raise SystemExit("Refusing to replace existing fixture resources; remove only stale test resources first.")
PUBLIC.write_bytes(crypto.dump_certificate(crypto.FILETYPE_PEM, cert))
CONFIG.write_text('''<network-security-config>
  <base-config cleartextTrafficPermitted="false" />
  <domain-config cleartextTrafficPermitted="true"><domain includeSubdomains="false">127.0.0.1</domain><domain includeSubdomains="false">localhost</domain></domain-config>
  <debug-overrides><trust-anchors><certificates src="@raw/web_fixture" /></trust-anchors></debug-overrides>
</network-security-config>''')
stats = ThreadingHTTPServer(("127.0.0.1", 18892), Stats)
threading.Thread(target=stats.serve_forever, daemon=True).start()
listener = socket.socket()
listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listener.bind(("127.0.0.1", 19443))
listener.listen(16)
print("HTTPS fixture ready: 19443; counters: 18892; public debug certificate generated", flush=True)
try:
    while True:
        client, _ = listener.accept()
        client.setblocking(True)
        threading.Thread(target=serve, args=(client,), daemon=True).start()
finally:
    listener.close()
    stats.shutdown()
    PUBLIC.unlink(missing_ok=True)
    CONFIG.unlink(missing_ok=True)
