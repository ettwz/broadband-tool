#!/usr/bin/env python3
"""Local test-only HTTP control endpoint for a fixed Android iperf peer."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse
import ipaddress
import json
import subprocess
from urllib.parse import urlsplit, parse_qs

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--device', required=True)
    parser.add_argument('--control-port', type=int, default=18890)
    parser.add_argument('--device-port', type=int, default=15202)
    args = parser.parse_args()
    device = str(ipaddress.IPv4Address(args.device))
    binary = ROOT / '.cache/iperf-build/host/src/iperf3'
    assert binary.is_file()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            url = urlsplit(self.path)
            if url.path == '/health':
                result = {'ready': True}
            elif url.path == '/client':
                protocol = parse_qs(url.query).get('protocol', ['tcp'])[0]
                if protocol not in ('tcp', 'udp'):
                    self.send_error(400)
                    return
                command = [str(binary), '-c', device, '-p', str(args.device_port),
                           '-4', '-t', '5', '--json-stream', '--forceflush', '--connect-timeout', '5000']
                if protocol == 'udp':
                    command += ['-u', '-b', '10M']
                try:
                    process = subprocess.run(command, text=True, capture_output=True, timeout=15)
                    events = [json.loads(line) for line in process.stdout.splitlines() if line.startswith('{')]
                    data = {event['event']: event['data'] for event in events}
                    result = {'exit': process.returncode, 'end': data.get('end', {}),
                              'connected': data.get('start', {}).get('connected', []),
                              'error': data.get('error') or process.stderr.strip()[:300]}
                except (subprocess.TimeoutExpired, ValueError) as error:
                    raw = getattr(error, 'stdout', b'') or b''
                    if isinstance(raw, bytes):
                        raw = raw.decode(errors='replace')
                    stages = [json.loads(line)['event'] for line in raw.splitlines() if line.startswith('{')]
                    result = {'exit': 1, 'error': f'{type(error).__name__}: stages={stages}'}
            else:
                self.send_error(404)
                return
            body = json.dumps(result).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', args.control_port), Handler)
    print(f'Test control ready on loopback:{args.control_port}; fixed device peer port {args.device_port}', flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
