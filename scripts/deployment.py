#!/usr/bin/env python3
"""Single-host Savix deployment gates. Python stdlib; no shell-sourced secrets."""
import argparse
import contextlib
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent
APPS = ('savix-backend', 'savix-frontend')
INFRA = ('savix-db', 'savix-redis', 'postgres-exporter', 'redis-exporter')


class Failure(Exception):
    pass


def require(condition, message):
    if not condition:
        raise Failure(message)


def atomic_json(path, value):
    temporary = path.with_suffix('.tmp')
    with temporary.open('x') as out:
        json.dump(value, out, indent=2)
        out.write('\n')
        out.flush()
        os.fsync(out.fileno())
    temporary.replace(path)


class Deployment:
    def __init__(self, args):
        self.a = args
        self.docker = ['docker', '--context', args.docker_context]
        # The explicit env file is authoritative. Do not inherit interpolation
        # variables, COMPOSE_FILE, Docker endpoints, or ambient application secrets.
        self.env = {k: v for k, v in os.environ.items()
                    if k in ('PATH', 'HOME', 'DOCKER_CONFIG', 'LANG', 'TMPDIR')}
        self.env.update(BACKEND_IMAGE=args.backend_image or '', FRONTEND_IMAGE=args.frontend_image or '')
        self.compose = self.docker + ['compose', '--profile', 'deployment', '--project-name', args.project,
                                      '--env-file', str(args.env_file),
                                      '-f', str(ROOT / 'compose.production.yml'),
                                      '-f', str(ROOT / 'compose.deploy.yml')]
        if args.compose_override:
            self.compose += ['-f', str(args.compose_override)]
        self.stage = 'preflight'
        self.record = None
        self.secrets = []
        self.model = None
        self.run_id = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ') + '-' + uuid.uuid4().hex[:8]

    def run(self, command, *, timeout=30, stdin=None, stdin_file=None, stdout=subprocess.PIPE):
        try:
            result = subprocess.run(command, env=self.env, input=stdin, stdin=stdin_file, stdout=stdout,
                                    stderr=subprocess.PIPE, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise Failure(f'{self.stage}: command timed out (details suppressed)') from None
        require(result.returncode == 0, f'{self.stage}: command failed (exit {result.returncode}; raw output suppressed)')
        return result.stdout

    def inspect(self, kind, name):
        return json.loads(self.run(self.docker + [kind, 'inspect', name]))[0]

    def name(self, service):
        return self.model['services'][service]['container_name']

    def image_id(self, reference):
        require(bool(re.fullmatch(r'sha256:[a-f0-9]{64}', reference) or
                     re.fullmatch(r'[\w./:-]+@sha256:[a-f0-9]{64}', reference) or
                     (re.fullmatch(r'[\w./:-]+:[\w.-]+', reference) and
                      reference.rsplit(':', 1)[1].lower() not in ('latest', 'stable', 'main', 'master', 'dev'))),
                'Use an immutable release tag, digest, or image ID; floating/untagged images are refused')
        return self.inspect('image', reference)['Id']

    def topology(self):
        result = {}
        for service in INFRA:
            item = self.inspect('container', self.name(service))
            require(item['State']['Running'], f'{service} must already be running')
            result[service] = {'id': item['Id'], 'mounts': sorted(item['Mounts'], key=lambda m: m['Destination'])}
        return result

    def db(self, program, arguments, *, stdin=None, stdout=subprocess.PIPE, timeout=30):
        # Credentials remain in the existing database container environment.
        # TCP forces password authentication; no password in argv or host logs.
        script = 'export PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5; exec ' + program + ' -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" ' + arguments
        return self.run(self.docker + ['exec', '-i', self.name('savix-db'), 'sh', '-c', script],
                        stdin=stdin, stdout=stdout, timeout=timeout)

    def preflight(self):
        a = self.a
        require(a.env_file.is_absolute() and a.env_file.is_file(), 'An existing absolute --env-file is required')
        require(a.env_file.stat().st_mode & 0o077 == 0, 'The runtime env file must be mode 0600 (or stricter)')
        for directory in (a.backup_dir, a.state_dir):
            require(directory.is_absolute() and directory.is_dir() and os.access(directory, os.W_OK),
                    'Backup and state directories must be existing writable absolute paths')
            require(directory.stat().st_mode & 0o022 == 0, 'Backup/state directories must not be writable by group/others')
            require(shutil.disk_usage(directory).free >= a.min_free_mb * 1024**2, 'Insufficient host free disk space')
        context = self.inspect('context', a.docker_context)
        require(context['Endpoints']['docker']['Host'].startswith('unix://'),
                'Run on the Docker host: only an explicit local Unix-socket context is supported')
        info = json.loads(self.run(self.docker + ['info', '--format', '{{json .}}']))
        self.daemon_id = info['ID']
        self.run(self.docker + ['compose', 'version'])
        self.rollback = None
        if a.rollback:
            self.rollback = json.loads(a.rollback.read_text())
            require(self.rollback['daemon_id'] == self.daemon_id and self.rollback['project'] == a.project,
                    'Rollback record belongs to a different Docker host/project')
            refs = {s: self.rollback['previous'][s]['id'] for s in APPS}
        else:
            refs = dict(zip(APPS, (a.backend_image, a.frontend_image)))
        require(all(refs.values()), 'Both backend and frontend image inputs are required')
        self.images = {s: {'reference': refs[s], 'id': self.image_id(refs[s])} for s in APPS}
        # Pin to inspected IDs, so a tag changing during the run cannot change the release.
        self.env.update(BACKEND_IMAGE=self.images[APPS[0]]['id'], FRONTEND_IMAGE=self.images[APPS[1]]['id'])
        self.model = json.loads(self.run(self.compose + ['config', '--format', 'json']))
        for kind in ('networks', 'volumes'):
            for value in self.model[kind].values():
                require(value.get('external'), 'Deployment resources must be external')
                self.inspect(kind[:-1], value['name'])
        services = self.model['services']
        for value in services.values():
            for key, secret in value.get('environment', {}).items():
                if any(marker in key for marker in ('PASSWORD', 'SECRET', 'TOKEN', 'DATA_SOURCE')) and secret:
                    self.secrets.append(str(secret))
        be, fe = (services[s]['environment'] for s in APPS)
        for key in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SMTP_HOST', 'SMTP_USERNAME',
                    'SMTP_PASSWORD', 'PROXY_SECRET', 'EMAIL_FROM', 'JWT_ISSUER', 'JWT_AUDIENCE'):
            require(isinstance(be.get(key), str) and bool(be[key].strip()), f'Required runtime setting {key} is blank')
        require(services['savix-migrate']['image'] == self.images[APPS[0]]['id'] and
                all(services[s]['image'] == self.images[s]['id'] for s in APPS), 'Compose must use the pinned candidate images')
        migration = services['savix-migrate']['environment']
        require(all(migration.get(k) == be[k] for k in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD'))
                and migration.get('MIGRATION_ENV') == 'prod' and migration.get('DB_SCHEMA') == 'public'
                and migration.get('MIGRATION_EXPECTED_HOST') == 'savix-db', 'Migration target must match the backed-up application database')
        require(be['DB_URL'] == 'jdbc:postgresql://savix-db:5432/savix', 'Expected Savix database URL is required')
        require(fe['PROXY_BASE'] == 'http://savix-backend:8000', 'PROXY_BASE must reach the Compose backend')
        require(be['PROXY_SECRET'] == fe['PROXY_SECRET'], 'Proxy secrets must match')
        for mount in services['savix-backend']['volumes']:
            if mount['type'] == 'bind':
                path = Path(mount['source'])
                require(path.is_absolute() and path.is_file() and path.stat().st_size > 0 and os.access(path, os.R_OK),
                        'JWT key files must be existing, readable, nonempty absolute files')
        self.infrastructure = self.topology()
        for service, volume in (('savix-db', 'savix-db-data'), ('savix-redis', 'savix-redis-existing-data')):
            expected = self.model['volumes'][volume]['name']
            require(any(m.get('Name') == expected for m in self.infrastructure[service]['mounts']),
                    f'{service} is not using the expected existing volume')
        db_info = self.inspect('container', self.name('savix-db'))
        actual = dict(entry.split('=', 1) for entry in db_info['Config']['Env'] if '=' in entry)
        require(actual.get('POSTGRES_DB') == 'savix' and actual.get('POSTGRES_USER') == be['DB_USERNAME']
                and actual.get('POSTGRES_PASSWORD') == be['DB_PASSWORD'], 'Database runtime credentials do not match configuration')
        for service in ('savix-db', 'savix-redis'):
            networks = self.inspect('container', self.name(service))['NetworkSettings']['Networks']
            require(self.model['networks']['savix_internal']['name'] in networks, f'{service} is not on the application network')
        query = b"SELECT current_setting('server_version_num'), pg_database_size(current_database());\n"
        version, size = self.db('psql', '-X -qAt -v ON_ERROR_STOP=1', stdin=query).decode().strip().split('|')
        require(160000 <= int(version) < 170000, 'PostgreSQL server must be version 16')
        for tool in ('pg_dump', 'pg_restore'):
            version_text = self.run(self.docker + ['exec', self.name('savix-db'), tool, '--version']).decode()
            require(re.search(r'\b16\.', version_text), 'PostgreSQL backup tools must be version 16')
        require(shutil.disk_usage(a.backup_dir).free >= max(a.min_free_mb * 1024**2, int(size) * 2),
                'Insufficient backup space (requires at least twice database size)')
        for mount in ('/', '/var/lib/postgresql/data'):
            free = self.run(self.docker + ['exec', self.name('savix-db'), 'df', '-Pk', mount]).decode().splitlines()[-1].split()[3]
            require(int(free) * 1024 >= a.min_free_mb * 1024**2, 'Insufficient Docker/data filesystem free space')
        self.previous = {}
        existing = self.run(self.docker + ['container', 'ls', '-a', '--format', '{{.Names}}']).decode().splitlines()
        for service in APPS:
            if self.a.rollback and self.name(service) not in existing:
                # Compose can fail after removing the old container. Recovery must
                # still be able to recreate it from the original release record.
                self.previous[service] = {'reference': None, 'id': None, 'container_id': None}
                continue
            item = self.inspect('container', self.name(service))
            # Stopped/unhealthy containers are allowed here so manual recovery works.
            image = item['Image']
            self.inspect('image', image)
            self.previous[service] = {'reference': item['Config']['Image'], 'id': image, 'container_id': item['Id']}
        if self.rollback:
            require(self.rollback['containers'] == {s: self.name(s) for s in APPS}, 'Rollback container identities do not match')
        print('Preflight passed: local images, config, keys, networks, volumes, disk and PostgreSQL 16.', flush=True)

    def start_record(self):
        self.record = self.a.state_dir / self.run_id
        self.record.mkdir(mode=0o700)
        atomic_json(self.record / 'release.json', {
            'created_at': self.run_id, 'daemon_id': self.daemon_id, 'project': self.a.project,
            'containers': {s: self.name(s) for s in APPS}, 'previous': self.previous,
            'candidate': self.images, 'rollback_source': str(self.a.rollback) if self.a.rollback else None})
        print(f'Previous image pair retained in {self.record / "release.json"}', flush=True)

    def backup(self):
        self.stage = 'backup'
        print('Creating and inspecting PostgreSQL backup.', flush=True)
        target = self.a.backup_dir / ('savix_deploy_' + self.run_id + '.dump')
        temporary = target.with_suffix('.dump.partial')
        # Do not delete even incomplete backups; operators may investigate them.
        with temporary.open('xb') as output:
            self.db('pg_dump', '--format=custom', stdout=output, timeout=self.a.backup_timeout)
            output.flush()
            os.fsync(output.fileno())
        require(temporary.stat().st_size > 0, 'Backup is empty')
        with temporary.open('rb') as source:
            self.run(self.docker + ['exec', '-i', self.name('savix-db'), 'pg_restore', '--list'],
                     stdin_file=source, stdout=subprocess.DEVNULL, timeout=self.a.backup_timeout)
        checksum = hashlib.sha256()
        with temporary.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                checksum.update(chunk)
        atomic_json(target.with_suffix('.json'), {'created_at': self.run_id, 'database': 'savix',
                    'postgres_major': 16, 'bytes': temporary.stat().st_size, 'sha256': checksum.hexdigest(),
                    'candidate': self.images, 'dump_inspected': True})
        temporary.replace(target)
        print(f'Backup gate passed: {target}', flush=True)

    def migrate(self, operation):
        self.stage = 'Flyway ' + operation
        print(self.stage + '.', flush=True)
        name = 'savix-migrate-' + self.run_id.lower() + '-' + operation
        try:
            self.run(self.compose + ['run', '--rm', '--no-deps', '--pull', 'never', '-T', '--name', name,
                                     'savix-migrate', 'db', operation], timeout=self.a.migration_timeout)
        finally:
            # A timed-out Compose client may leave its one-off container running.
            # Stop only this attempt's one-off before releasing the host lock.
            result = subprocess.run(self.docker + ['container', 'inspect', name], env=self.env,
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
            if result.returncode == 0:
                self.run(self.docker + ['rm', '-f', name])

    def matches(self, service):
        item = self.inspect('container', self.name(service))
        require(item['State']['Running'] and item['Image'] == self.images[service]['id']
                and item['Config']['Image'] == self.images[service]['id'], f'{service} is not running the pinned candidate image')

    def health(self, service):
        self.stage = service + ' health'
        for attempt in range(self.a.health_attempts):
            try:
                self.matches(service)
                if service == 'savix-backend':
                    body = self.run(self.docker + ['exec', self.name(service), 'wget', '-q', '-T', '5', '-O', '-',
                                                 'http://127.0.0.1:8000/actuator/health/readiness'], timeout=10)
                    require(json.loads(body).get('status') == 'UP', 'Backend health is not UP')
                else:
                    probe = "(async()=>{for(const p of ['/api/health','/login']){const r=await fetch('http://127.0.0.1:3000'+p,{redirect:'manual',signal:AbortSignal.timeout(5000)});if(r.status!==200)throw Error();if(p==='/api/health'&&(await r.json()).status!=='UP')throw Error();}})().catch(()=>process.exit(1))"
                    self.run(self.docker + ['exec', self.name(service), 'node', '-e', probe], timeout=15)
                self.matches(service)
                print(service + ' health and candidate image verified.', flush=True)
                return
            except (Failure, ValueError):
                if attempt + 1 < self.a.health_attempts:
                    time.sleep(self.a.health_interval)
        raise Failure(self.stage + ' failed after bounded retries')

    def rollout(self, service):
        self.stage = service + ' rollout'
        print(self.stage + '.', flush=True)
        self.run(self.compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never', service], timeout=180)
        self.health(service)

    def diagnostics(self):
        # Bounded, redacted excerpts only; never print inspect/config/env payloads.
        for service in APPS:
            try:
                result = subprocess.run(self.docker + ['logs', '--tail', '80', '--since', '10m', self.name(service)],
                                        env=self.env, capture_output=True, timeout=10)
                content = (result.stdout + result.stderr).decode(errors='replace')[-32768:]
                content = re.sub(r'-----BEGIN .*?-----.*?-----END .*?-----', '[REDACTED KEY]', content, flags=re.S)
                for secret in sorted(self.secrets, key=len, reverse=True):
                    content = content.replace(secret, '[REDACTED]')
                content = re.sub(r'(?i)(password|secret|authorization|token)\s*[:=]\s*\S+', r'\1=[REDACTED]', content)
                path = self.record / (service + '.log')
                path.write_text(content)
                print(f'Bounded redacted diagnostics: {path}', flush=True)
            except Exception:
                print(f'Diagnostics unavailable for {service}.', flush=True)

    def execute(self):
        self.preflight()
        if self.a.preflight:
            return
        self.start_record()
        try:
            if not self.a.rollback:
                self.backup()
                if self.a.backup_only:
                    return
                self.migrate('validate')
                self.migrate('migrate')
            self.rollout(APPS[0])
            self.rollout(APPS[1])
            self.health(APPS[0])
            require(self.topology() == self.infrastructure, 'Infrastructure identity/mounts changed during deployment')
            atomic_json(self.record / 'success.json', {'result': 'healthy', 'candidate': self.images})
            print('Deployment healthy. No image, volume or backup cleanup performed.', flush=True)
        except BaseException:
            atomic_json(self.record / 'failure.json', {'stage': self.stage})
            if 'rollout' in self.stage or 'health' in self.stage:
                self.diagnostics()
            print(f'Deployment failed at {self.stage}; previous image pair retained. No automatic rollback.', file=sys.stderr)
            raise


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--preflight', action='store_true', help='Read-only checks; no files/containers created')
    mode.add_argument('--rollback', type=Path, help='Restore the previous image pair from this release.json; skip backup/migrations')
    mode.add_argument('--backup-only', action='store_true')
    parser.add_argument('--env-file', type=Path, required=True)
    parser.add_argument('--backend-image')
    parser.add_argument('--frontend-image')
    parser.add_argument('--docker-context', required=True, help='Explicit local Unix-socket Docker context')
    parser.add_argument('--backup-dir', type=Path, required=True)
    parser.add_argument('--state-dir', type=Path, required=True)
    parser.add_argument('--lock-file', type=Path, required=True, help='Same absolute host path for every manual/automated invocation')
    parser.add_argument('--project', default='savix')
    parser.add_argument('--compose-override', type=Path, help='Isolated rehearsal only; redirects names, ports, networks and volumes')
    for name, default in [('min-free-mb', 1024), ('health-attempts', 30), ('health-interval', 5),
                          ('backup-timeout', 600), ('migration-timeout', 300)]:
        parser.add_argument('--' + name, type=int, default=default)
    args = parser.parse_args()
    for name in ('min_free_mb', 'health_attempts', 'health_interval', 'backup_timeout', 'migration_timeout'):
        require(getattr(args, name) > 0, name + ' must be positive')
    require(args.lock_file.is_absolute() and args.lock_file.parent.is_dir(), 'Lock requires an existing parent and absolute path')
    require(not args.rollback or not (args.backend_image or args.frontend_image), 'Rollback uses the recorded IDs; omit candidate inputs')
    return args


def main():
    os.umask(0o077)
    def interrupted(signum, frame):
        raise Failure('Interrupted; inspect the retained release record before retrying')
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        args = parse_args()
        with contextlib.ExitStack() as stack:
            if not args.preflight:
                # fcntl.flock uses the same host advisory lock as util-linux flock.
                # Keep this inode: unlinking a lock file permits overlapping locks.
                fd = os.open(args.lock_file, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
                lock = stack.enter_context(os.fdopen(fd, 'r+'))
                try:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    raise Failure('Another deployment/backup/rollback holds the host lock') from None
            Deployment(args).execute()
        return 0
    except Failure as error:
        print('ERROR: ' + str(error), file=sys.stderr)
    except Exception:
        # Exceptions may embed secret env/config/SQL. Never print raw exceptions.
        print('ERROR: deployment operation failed; raw details suppressed. Check configuration and protected records.', file=sys.stderr)
    return 1


if __name__ == '__main__':
    sys.exit(main())
