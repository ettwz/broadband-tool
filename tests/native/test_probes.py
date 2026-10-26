"""Real unprivileged Linux socket checks; these are not Android device proof."""
import json
from pathlib import Path
import subprocess
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
BINARY = ROOT / '.cache/native-tests/probe_smoke'


class ProbeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        BINARY.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['gcc', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
                        str(ROOT / 'native/probes/probe_engine.c'),
                        str(ROOT / 'native/probes/probe_smoke.c'), '-o', str(BINARY)], check=True)

    def run_probe(self, mode, target):
        result = subprocess.run([str(BINARY), mode, target], text=True,
                                capture_output=True, timeout=5)
        events = [json.loads(line) for line in result.stdout.splitlines()]
        return result.returncode, events

    def test_icmp_actual_loopback_reply(self):
        code, events = self.run_probe('ping', '127.0.0.1')
        self.assertEqual(code, 0, events)
        samples = [event['data'] for event in events if event['event'] == 'sample']
        self.assertEqual([sample['sequence'] for sample in samples], [1, 2])
        self.assertTrue(all(sample['status'] == 'reply' and sample['rttMs'] >= 0 for sample in samples))
        self.assertEqual(events[-1]['data']['received'], 2)

    def test_trace_receives_port_unreachable(self):
        code, events = self.run_probe('trace', '127.0.0.1')
        self.assertEqual(code, 0, events)
        hops = [event['data'] for event in events if event['event'] == 'hop']
        self.assertEqual(len(hops), 3)
        self.assertTrue(all(hop['icmpType'] == 3 and hop['icmpCode'] == 3 for hop in hops))
        self.assertTrue(events[-1]['data']['reached'])

    def test_invalid_and_non_unicast_targets_fail_before_network_io(self):
        for target in ['999.1.1.1', 'example.com', '0.0.0.0', '224.0.0.1', '255.255.255.255', '127.0.0.1;id']:
            with self.subTest(target=target):
                code, events = self.run_probe('trace', target)
                self.assertEqual(code, 1)
                self.assertEqual(events, [{'event': 'error', 'data': {'operation': 'input', 'errno': 22}}])

    def test_cancellation_interrupts_a_long_run(self):
        started = time.monotonic()
        code, events = self.run_probe('cancel', '127.0.0.1')
        self.assertEqual(code, 3)
        self.assertLess(time.monotonic() - started, 2)
        self.assertEqual(events[-1]['data']['sent'], 1)


if __name__ == '__main__':
    unittest.main()
