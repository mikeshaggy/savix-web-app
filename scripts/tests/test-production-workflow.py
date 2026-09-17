#!/usr/bin/env python3
"""SHG-17 shell/flow regression checks. PyYAML required; no Docker/production access."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = yaml.load((ROOT / '.github/workflows/deploy.yml').read_text(), Loader=yaml.BaseLoader)
SHA = 'a' * 40
IMAGE_ID = 'sha256:' + 'b' * 64

# This fake records exact argv and maintains a local image store. Unexpected
# commands fail, so no unnoticed deployment/registry/cleanup command can pass.
FAKE_DOCKER = r'''#!/usr/bin/env python3
import json, os, pathlib, sys
p = pathlib.Path(os.environ['FAKE_STATE'])
s = json.loads(p.read_text())
a = sys.argv[1:]
assert a[:2] == ['--context', 'default'], a
a = a[2:]
s['calls'].append(a)
p.write_text(json.dumps(s))
def save(): p.write_text(json.dumps(s))
if a[:2] == ['context', 'inspect']:
    print(s.get('endpoint', 'unix:///var/run/docker.sock'))
elif a[0] == 'info':
    print(s.get('platform', 'linux/aarch64') if a[-1] == '{{.OSType}}/{{.Architecture}}' else os.environ['GITHUB_WORKSPACE'])
elif a[:2] in (['compose', 'version'], ['buildx', 'version']):
    print('fixture version')
elif a[:2] == ['image', 'inspect']:
    image = s['images'].get(a[2])
    if not image: sys.exit(1)
    print(image['id'] if a[-1] == '{{.Id}}' else image['platform'] + ' ' + image['sha'])
elif a[:2] == ['image', 'tag']:
    s['images'][a[3]] = s['images'][a[2]]
    save()
elif a[:2] == ['buildx', 'build']:
    if s.get('fail') == ('frontend-validation' if '--target' in a else a[-1] + '-build'): sys.exit(23)
    if '--iidfile' in a:
        ident = 'sha256:' + 'b' * 64
        s['images'][ident] = {'id': ident, 'platform': s.get('built_platform', 'linux/arm64'), 'sha': os.environ['GITHUB_SHA']}
        pathlib.Path(a[a.index('--iidfile') + 1]).write_text(ident)
        if s.get('collision'):
            s['images']['savix-' + a[-1] + ':git-' + os.environ['GITHUB_SHA']] = {'id': 'sha256:' + 'c' * 64, 'platform': 'linux/arm64', 'sha': os.environ['GITHUB_SHA']}
        save()
elif a[0] == 'run':
    if s.get('fail') == 'backend-validation': sys.exit(23)
elif a[:2] == ['rm', '-f'] and a[2] == 'savix-ci-17-1-backend':
    pass
else:
    raise AssertionError(a)
'''

FAKE_DEPLOY = r'''#!/usr/bin/env python3
import json, os, pathlib, sys
p = pathlib.Path(os.environ['FAKE_STATE'])
s = json.loads(p.read_text())
s['deploy'].append(sys.argv[1:])
p.write_text(json.dumps(s))
stage = 'preflight' if '--preflight' in sys.argv else 'deployment'
if s.get('fail') == stage: sys.exit(29)
'''


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='shg17-', dir=ROOT / '.local/shg-17')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        (self.root / 'scripts/ci').mkdir(parents=True)
        (self.root / 'bin').mkdir()
        for component in ('backend', 'frontend'):
            (self.root / component).mkdir()
            shutil.copyfile(ROOT / component / 'Dockerfile', self.root / component / 'Dockerfile')
        shutil.copyfile(ROOT / 'scripts/ci/production-build.sh', self.root / 'scripts/ci/production-build.sh')
        for name, content in {'docker': FAKE_DOCKER, 'git': '#!/bin/sh\nprintf "%s\\n" "$GITHUB_SHA"\n',
                              'stat': '#!/bin/sh\nprintf "0\\n"\n'}.items():
            p = self.root / 'bin' / name
            p.write_text(content.replace('#!/usr/bin/env python3', '#!' + sys.executable))
            p.chmod(0o755)
        p = self.root / 'scripts/deploy.sh'
        p.write_text(FAKE_DEPLOY.replace('#!/usr/bin/env python3', '#!' + sys.executable))
        p.chmod(0o755)
        self.state = self.root / 'state.json'
        self.state.write_text(json.dumps({'calls': [], 'deploy': [], 'images': {}}))
        self.env = dict(os.environ, PATH=str(self.root / 'bin') + ':' + os.environ['PATH'],
                        FAKE_STATE=str(self.state), GITHUB_SHA=SHA, GITHUB_WORKSPACE=str(self.root),
                        GITHUB_RUN_ID='17', GITHUB_RUN_ATTEMPT='1', RUNNER_TEMP=str(self.root),
                        SAVIX_DOCKER_CONTEXT='default', BACKEND_IMAGE='savix-backend:git-' + SHA,
                        FRONTEND_IMAGE='savix-frontend:git-' + SHA)
        for key in ('SAVIX_RUNTIME_ENV', 'SAVIX_BACKUP_DIR', 'SAVIX_STATE_DIR', 'SAVIX_LOCK_FILE'):
            self.env[key] = '/protected/path with spaces/$literal/' + key

    def data(self):
        return json.loads(self.state.read_text())

    def change(self, **kwargs):
        value = self.data()
        value.update(kwargs)
        self.state.write_text(json.dumps(value))

    def shell(self, command):
        return subprocess.run(['bash', '--noprofile', '--norc', '-e', '-o', 'pipefail', '-c', command],
                              cwd=self.root, env=self.env, capture_output=True, text=True)

    def helper(self, mode):
        return self.shell('bash scripts/ci/production-build.sh ' + mode)

    def flow(self):
        # GitHub steps without an explicit if use success(). Exercise the actual
        # YAML run bodies, stopping at the first failure; deployment is a fake.
        for step in WORKFLOW['jobs']['deploy']['steps']:
            if step.get('id'):
                result = self.shell(step['run'])
                if result.returncode:
                    return step['id'], result
        return 'success', None

    def test_workflow_security_and_sequential_gates(self):
        self.assertEqual(WORKFLOW['on'], {'push': {'branches': ['main']}})
        self.assertEqual(WORKFLOW['permissions'], {'contents': 'read'})
        self.assertEqual(WORKFLOW['concurrency'], {'group': 'savix-production', 'cancel-in-progress': 'false'})
        job = WORKFLOW['jobs']['deploy']
        self.assertEqual(job['runs-on'], ['self-hosted', 'linux', 'arm64', 'savix-production'])
        for guard in ("github.event_name == 'push'", "github.ref == 'refs/heads/main'",
                      "github.repository == 'mikeshaggy/savix-web-app'", "vars.SAVIX_PRODUCTION_ENABLED == 'true'"):
            self.assertIn(guard, job['if'])
        self.assertEqual(job['environment'], 'production')
        self.assertIn('timeout-minutes', job)
        checkout = next(s for s in job['steps'] if 'uses' in s)
        self.assertRegex(checkout['uses'], r'^actions/checkout@[a-f0-9]{40}$')
        self.assertEqual(checkout['with']['persist-credentials'], 'false')
        self.assertEqual(checkout['with']['ref'], '${{ github.sha }}')
        for step in job['steps']:
            self.assertNotIn('continue-on-error', step)
            if 'run' in step:
                self.assertIn('timeout-minutes', step)
            if step.get('id'):
                self.assertNotIn('if', step)

    def test_workspace_guard_before_checkout(self):
        # Emulate the Linux absolute work root in a local temporary tree.
        # macOS resolves /var to /private/var, unlike the production Linux host.
        work = str(self.root / 'runner/work')
        guard = WORKFLOW['jobs']['deploy']['steps'][0]['run'].replace('/var/lib/savix-runner/work', work)
        self.env.update(RUNNER_OS='Linux', RUNNER_ARCH='ARM64',
                        RUNNER_WORKSPACE=work + '/savix-web-app',
                        GITHUB_WORKSPACE=work + '/savix-web-app/savix-web-app')
        self.assertEqual(self.shell(guard).returncode, 0)
        for value in ('/home/mikeshaggy/homelab/savix/savix-web-app', '/var/lib/savix-runner/work-other/repo'):
            self.env['GITHUB_WORKSPACE'] = value
            self.assertNotEqual(self.shell(guard).returncode, 0)
        self.env['GITHUB_WORKSPACE'] = work + '/savix-web-app/savix-web-app'
        self.env['SAVIX_RUNTIME_ENV'] = self.env['GITHUB_WORKSPACE'] + '/runtime.env'
        self.assertNotEqual(self.shell(guard).returncode, 0)
        self.assertEqual(self.data()['calls'], [])

    def test_validation_and_build_failures_never_call_deploy(self):
        for fail, expected in [('backend-validation', 'backend_validation'),
                               ('frontend-validation', 'frontend_validation'),
                               ('backend-build', 'backend_image'), ('frontend-build', 'frontend_image')]:
            with self.subTest(fail=fail):
                self.change(fail=fail, calls=[], images={}, deploy=[])
                stage, result = self.flow()
                self.assertEqual(stage, expected, result)
                self.assertEqual(self.data()['deploy'], [])

    def test_preflight_failure_never_calls_mutating_deploy(self):
        self.change(fail='preflight')
        self.assertEqual(self.flow()[0], 'preflight')
        self.assertEqual(len(self.data()['deploy']), 1)
        self.assertIn('--preflight', self.data()['deploy'][0])

    def test_deployment_failure_is_propagated_without_retry(self):
        self.change(fail='deployment')
        stage, result = self.flow()
        self.assertEqual(stage, 'deployment')
        self.assertEqual(result.returncode, 29)
        self.assertEqual(len(self.data()['deploy']), 2)

    def test_success_passes_exact_quoted_shg16_inputs(self):
        stage, result = self.flow()
        self.assertEqual(stage, 'success', result)
        preflight, deploy = self.data()['deploy']
        self.assertEqual(preflight, ['--preflight'] + deploy)
        values = dict(zip(deploy[::2], deploy[1::2]))
        self.assertEqual(values, {'--backend-image': self.env['BACKEND_IMAGE'],
                                 '--frontend-image': self.env['FRONTEND_IMAGE'],
                                 '--env-file': self.env['SAVIX_RUNTIME_ENV'],
                                 '--backup-dir': self.env['SAVIX_BACKUP_DIR'],
                                 '--state-dir': self.env['SAVIX_STATE_DIR'],
                                 '--lock-file': self.env['SAVIX_LOCK_FILE'], '--docker-context': 'default'})
        help_result = subprocess.run([str(ROOT / 'scripts/deploy.sh'), '--help'],
                                     capture_output=True, text=True, check=True)
        for flag in values:
            self.assertIn(flag, help_result.stdout)
        for build in (a for a in self.data()['calls'] if a[:2] == ['buildx', 'build']):
            self.assertIn('--load', build)
            self.assertEqual(build[build.index('--platform') + 1], 'linux/arm64')
            self.assertNotIn('--push', build)
            if '--target' not in build:
                self.assertEqual(build[build.index('--build-arg') + 1], 'VCS_REF=' + SHA)
                self.assertNotIn('--tag', build)
        backend = next(a for a in self.data()['calls'] if a[0] == 'run')
        self.assertEqual(backend[-5:], ['./mvnw', '-B', '-ntp', 'verify', '-Ppostgres-it'])
        self.assertIn('127.0.0.1', ' '.join(backend))
        self.assertIn('--network', backend)
        self.assertNotIn('TESTCONTAINERS_RYUK_DISABLED', ' '.join(backend))
        frontend = next(a for a in self.data()['calls'] if '--target' in a)
        self.assertEqual(frontend[frontend.index('--target') + 1], 'build')
        dockerfile = (ROOT / 'frontend/Dockerfile').read_text()
        self.assertIn('RUN npm ci', dockerfile)
        self.assertIn('RUN npm run lint && npm run build', dockerfile)

    def test_existing_valid_tag_is_reused_without_build(self):
        self.change(images={self.env['BACKEND_IMAGE']: {'id': IMAGE_ID, 'platform': 'linux/arm64', 'sha': SHA}})
        self.assertEqual(self.helper('build-backend').returncode, 0)
        self.assertFalse(any(a[:2] in (['buildx', 'build'], ['image', 'tag']) for a in self.data()['calls']))

    def test_existing_wrong_tag_is_never_overwritten(self):
        for platform, sha in [('linux/amd64', SHA), ('linux/arm64', 'c' * 40)]:
            self.change(calls=[], images={self.env['BACKEND_IMAGE']: {'id': IMAGE_ID, 'platform': platform, 'sha': sha}})
            self.assertNotEqual(self.helper('build-backend').returncode, 0)
            self.assertFalse(any(a[:2] in (['buildx', 'build'], ['image', 'tag']) for a in self.data()['calls']))

    def test_bad_built_architecture_never_gets_release_tag(self):
        self.change(built_platform='linux/amd64')
        self.assertNotEqual(self.helper('build-backend').returncode, 0)
        self.assertNotIn(self.env['BACKEND_IMAGE'], self.data()['images'])

    def test_tag_collision_during_build_fails_without_overwrite(self):
        self.change(collision=True)
        self.assertNotEqual(self.helper('build-backend').returncode, 0)
        self.assertFalse(any(a[:2] == ['image', 'tag'] for a in self.data()['calls']))

    def test_remote_docker_rejected_before_build(self):
        self.change(endpoint='ssh://example.invalid')
        self.assertNotEqual(self.helper('build-backend').returncode, 0)
        self.assertEqual(len(self.data()['calls']), 1)

    def test_summary_does_not_read_host_secrets(self):
        step = WORKFLOW['jobs']['deploy']['steps'][-1]
        self.assertEqual(step['if'], '${{ always() }}')
        summary = self.root / 'summary.md'
        self.env['GITHUB_STEP_SUMMARY'] = str(summary)
        self.env.update({key: 'failure' if key == 'DEPLOYMENT_RESULT' else 'success' for key in step['env']})
        self.assertEqual(self.shell(step['run']).returncode, 0)
        text = summary.read_text()
        self.assertIn(SHA, text)
        self.assertIn('| SHG-16 deployment | failure |', text)
        self.assertNotIn('/protected/', text)
        self.assertEqual(self.data()['calls'], [])


if __name__ == '__main__':
    (ROOT / '.local/shg-17').mkdir(parents=True, exist_ok=True)
    unittest.main(verbosity=2)
