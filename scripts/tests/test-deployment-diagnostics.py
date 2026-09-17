#!/usr/bin/env python3
"""Focused diagnostics regressions; synthetic logs, no Docker or production access."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('deployment', ROOT / 'scripts/deployment.py')
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)
LIMIT = 32 * 1024


class DiagnosticsTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='savix-diagnostics-')
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.args = SimpleNamespace(
            docker_context='unused-local-fixture', backend_image='synthetic:backend',
            frontend_image='synthetic:frontend', project='diagnostic-fixture',
            env_file=self.directory / 'unused.env', compose_override=None,
            state_dir=self.directory, rollback=None, preflight=False, backup_only=False,
            lock_file=self.directory / 'fixture.lock')
        self.subject = deployment.Deployment(self.args)
        self.subject.record = self.directory / 'record'
        self.subject.record.mkdir(mode=0o700)
        self.subject.model = {'services': {s: {'container_name': 'fixture-' + s} for s in deployment.APPS}}
        self.console = io.StringIO()

    def capture(self, stdout, stderr='', secrets=()):
        self.subject.secrets = list(secrets)
        result = subprocess.CompletedProcess([], 0, stdout.encode(), stderr.encode())
        with patch.object(deployment.subprocess, 'run', return_value=result) as run, \
                contextlib.redirect_stdout(self.console), contextlib.redirect_stderr(self.console):
            self.subject.diagnostics()
        self.assertEqual(run.call_count, 2)
        texts = []
        for service in deployment.APPS:
            path = self.subject.record / (service + '.log')
            self.assertLessEqual(path.stat().st_size, LIMIT)
            texts.append(path.read_text(encoding='utf-8'))
        return texts

    def assert_absent(self, fragments, texts):
        # Never include captured raw logs or credentials in assertion output.
        for text in [*texts, self.console.getvalue()]:
            self.assertTrue(all(fragment not in text for fragment in fragments),
                            'Sensitive material survived in diagnostics or console')

    def test_authorization_entire_value_and_case_insensitive_headers(self):
        for header in ('Authorization', 'authorization', 'aUtHoRiZaTiOn'):
            with self.subTest(header=header):
                texts = self.capture(header + ': Bearer synthetic.bearer.token\r\nordinary next line\n')
                self.assert_absent(('Bearer', 'synthetic', 'bearer', '.token'), texts)
                for text in texts:
                    self.assertEqual(text, 'Authorization: [REDACTED]\r\nordinary next line\n'.replace('\r\n', '\n'))

    def test_configured_secret_crossing_output_boundary(self):
        secret = 'configured-prefix-CREDENTIAL-suffix-fragment'
        for split in (8, len(secret) // 2, len(secret) - 8):
            with self.subTest(split=split):
                tail = '\n' + 'ordinary log\n' * (LIMIT // 13 + 1)
                tail = (tail + 'x' * LIMIT)[:LIMIT - (len(secret) - split)]
                raw = 'older log\n' * 100 + secret + tail
                self.assertEqual(raw[-LIMIT:], secret[split:] + tail)
                texts = self.capture(raw, secrets=(secret,))
                self.assert_absent((secret[:split], secret[split:]), texts)
                for text in texts:
                    self.assertTrue(text.endswith(tail))

    def test_pem_crossing_output_boundary(self):
        material = 'SYNTHETICKEYMATERIAL0123456789'
        pem = '-----BEGIN PRIVATE KEY-----\n' + (material + '\n') * 1500 + '-----END PRIVATE KEY-----\n'
        for tail in ('ordinary final log\n', 'ordinary log\n' * 2500):
            raw = 'older logs\n' + pem + tail
            self.assertTrue(raw.index('-----BEGIN') < len(raw) - LIMIT < raw.index('-----END') + 25)
            texts = self.capture(raw)
            self.assert_absent((material, 'PRIVATE KEY', '0123456789'), texts)
            for text in texts:
                self.assertEqual(text, 'older logs\n[REDACTED KEY]\n' + tail)

    def test_known_secrets_assignments_and_ordinary_logs(self):
        secret = 'known-configured-credential'
        texts = self.capture('ordinary startup\n' + secret + '\n',
                             'PaSsWoRd=synthetic-pass SECRET: synthetic-secret token=synthetic-token\nhealthy\n',
                             secrets=(secret,))
        self.assert_absent((secret, 'synthetic-pass', 'synthetic-secret', 'synthetic-token'), texts)
        for text in texts:
            self.assertTrue(text.startswith('ordinary startup\n[REDACTED]\n'))
            self.assertTrue(text.endswith('healthy\n'))
            self.assertEqual(text.count('[REDACTED]'), 4)

    def test_redaction_across_captured_stdout_stderr(self):
        secret = 'split-configured-credential'
        texts = self.capture('ordinary\n' + secret[:10], secret[10:] + '\n', secrets=(secret,))
        self.assert_absent((secret[:10], secret[10:]), texts)
        self.assertEqual(texts, ['ordinary\n[REDACTED]\n'] * 2)

    def test_ordinary_and_multibyte_output_remains_bounded_and_readable(self):
        for raw in ('ordinary log\n' * LIMIT, 'zwykły dziennik 🙂\n' * LIMIT):
            texts = self.capture(raw)
            expected = raw.encode()[-LIMIT:].decode(errors='ignore')
            self.assertEqual(texts, [expected] * 2)

    def test_failure_diagnostics_private_and_usable(self):
        secret = 'configured-failure-credential'
        self.subject.secrets = [secret]
        self.subject.daemon_id = 'fixture-daemon'
        self.subject.previous = self.subject.images = {s: {'id': 'fixture-image'} for s in deployment.APPS}

        def fail_rollout(service):
            self.subject.stage = service + ' health'
            raise deployment.Failure('Synthetic health failure')

        result = subprocess.CompletedProcess([], 0, ('ordinary failure context\n' + secret + '\n').encode(), b'')
        previous_umask = os.umask(0o022)
        try:
            with patch.object(deployment, 'parse_args', return_value=self.args), \
                    patch.object(deployment, 'Deployment', return_value=self.subject), \
                    patch.object(deployment.signal, 'signal'), \
                    patch.object(self.subject, 'preflight'), patch.object(self.subject, 'backup'), \
                    patch.object(self.subject, 'migrate'), \
                    patch.object(self.subject, 'rollout', side_effect=fail_rollout), \
                    patch.object(deployment.subprocess, 'run', return_value=result), \
                    contextlib.redirect_stdout(self.console), contextlib.redirect_stderr(self.console):
                self.assertEqual(deployment.main(), 1)
        finally:
            os.umask(previous_umask)
        self.assertEqual(stat.S_IMODE(self.subject.record.stat().st_mode), 0o700)
        texts = []
        for path in self.subject.record.iterdir():
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            texts.append(path.read_text())
        self.assert_absent((secret,), texts)
        self.assertEqual(json.loads((self.subject.record / 'failure.json').read_text()),
                         {'stage': 'savix-backend health'})
        for service in deployment.APPS:
            self.assertEqual((self.subject.record / (service + '.log')).read_text(),
                             'ordinary failure context\n[REDACTED]\n')
        self.assertIn('previous image pair retained', self.console.getvalue())

    def test_capture_failure_does_not_print_raw_exception(self):
        with patch.object(deployment.subprocess, 'run', side_effect=RuntimeError('synthetic-raw-secret')), \
                contextlib.redirect_stdout(self.console):
            self.subject.diagnostics()
        self.assert_absent(('synthetic-raw-secret',), [])
        self.assertFalse(list(self.subject.record.iterdir()))
        self.assertEqual(self.console.getvalue().count('Diagnostics unavailable'), 2)


if __name__ == '__main__':
    unittest.main(verbosity=2)
