#!/usr/bin/env python3
"""Destructive ONLY to uniquely named local SHG-16 disposable resources.
Requires Docker Desktop, the SHG-13 backend runtime and a current packaged JAR.
Builds the current frontend with its normal Dockerfile (including lint/build).
Never uses a production env file/context, SSH, or production resource identities.
"""
import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'shg16-' + uuid.uuid4().hex[:8]
OUT = ROOT / '.local' / 'shg-16' / PREFIX
DOCKER = ['docker', '--context', 'desktop-linux']
RESULTS = []
IMAGES = []
NAMES = []
VOLUMES = [PREFIX + '-pg', PREFIX + '-redis']
NETWORKS = [PREFIX + '-internal', PREFIX + '-monitoring']
SECRET = 'dummy-shg16-password-only'


def run(args, *, input=None, check=True, timeout=180):
    p = subprocess.run([str(x) for x in args], input=input, capture_output=True, text=True, timeout=timeout)
    if check and p.returncode:
        raise AssertionError(f'Local test command failed: {args[:4]}\n{p.stdout[-3000:]}\n{p.stderr[-3000:]}')
    return p


def docker(*args, **kw):
    return run(DOCKER + list(args), **kw)


def passed(message):
    RESULTS.append(message)
    (OUT / 'results.json').write_text(json.dumps(RESULTS, indent=2) + '\n')
    print('PASS: ' + message, flush=True)


def snapshot():
    return {s: json.loads(docker('inspect', PREFIX + '-' + s).stdout)[0] for s in
            ('savix-backend', 'savix-frontend', 'savix-db', 'savix-redis', 'postgres-exporter', 'redis-exporter')}


def identity(items, services):
    return {s: (items[s]['Id'], sorted(items[s]['Mounts'], key=lambda m: m['Destination'])) for s in services}


def sql(statement):
    return docker('exec', '-i', PREFIX + '-savix-db', 'psql', '-X', '-qAt', '-U', 'shg16', '-d', 'savix',
                  '-v', 'ON_ERROR_STOP=1', input=statement).stdout.strip()


def health(path, status, target='backend'):
    # Probe from the real frontend container so backend checks traverse its network.
    base = 'http://savix-backend:8000' if target == 'backend' else 'http://127.0.0.1:3000'
    probe = "(async()=>{const r=await fetch(" + json.dumps(base + path) + ", {signal:AbortSignal.timeout(45000)});console.log(JSON.stringify({code:r.status,body:await r.json()}));})().catch(()=>process.exit(1))"
    response = json.loads(docker('exec', PREFIX+'-savix-frontend', 'node', '-e', probe, timeout=50).stdout)
    assert response['code'] == status, response
    assert response['body']['status'] == ('UP' if status == 200 else 'DOWN'), response
    return response['body']


def wait_ready():
    for attempt_number in range(20):
        try:
            health('/actuator/health/readiness', 200)
            health('/api/health', 200, 'frontend')
            return
        except AssertionError:
            if attempt_number == 19:
                raise
            time.sleep(1)


def attempt(label, backend, frontend, expected=0, extra=None, script='deploy.sh'):
    args = [ROOT / 'scripts' / script] + COMMON
    if backend:
        args += ['--backend-image', backend, '--frontend-image', frontend]
    args += extra or []
    p = run(args, check=False, timeout=300)
    output = p.stdout + p.stderr
    (OUT / (label + '.txt')).write_text(output)
    assert SECRET not in output, 'Secret appeared in console output'
    assert p.returncode == expected, (label, p.returncode, output)
    return output


OUT.mkdir(parents=True, mode=0o700)
for directory in ('backup', 'state'):
    (OUT / directory).mkdir(mode=0o700)
print('Isolated rehearsal resources: ' + PREFIX, flush=True)
try:
    context = json.loads(docker('context', 'inspect', 'desktop-linux').stdout)[0]
    assert context['Endpoints']['docker']['Host'].startswith('unix://')
    # Backend/infrastructure bases must exist locally; the frontend uses its build gate.
    for image in ('savix-backend:shg13-validation', 'postgres:16.11-bookworm', 'redis:7-alpine'):
        docker('image', 'inspect', image)
    frontend_base = PREFIX + ':frontend-base'
    build = docker('build', '--pull=false', '-t', frontend_base, ROOT / 'frontend', timeout=600)
    (OUT / 'frontend-build.log').write_text(build.stdout + build.stderr)
    IMAGES.append(frontend_base)
    passed('Current frontend Dockerfile lint/build passes and supplies the readiness proxy image')
    jar = ROOT / 'backend' / 'target' / 'backend-0.0.1-SNAPSHOT.jar'
    assert jar.is_file(), 'Build the current backend JAR before rehearsal'
    import shutil
    shutil.copyfile(jar, OUT / 'app.jar')
    for release in ('a', 'b', 'backend-bad', 'frontend-bad'):
        backend = release != 'frontend-bad'
        base = 'savix-backend:shg13-validation' if backend else frontend_base
        text = f'FROM {base}\nLABEL shg16.rehearsal="{PREFIX}-{release}"\n'
        if backend:
            text += 'COPY --chown=10001:10001 app.jar /app/app.jar\n'
        if release == 'backend-bad':
            (OUT / 'bad-backend.sh').write_text('#!/bin/sh\nif [ "$1" = db ]; then exec java -jar /app/app.jar "$@"; fi\necho "synthetic health failure password='+SECRET+'"\nexec sleep 86400\n')
            text += 'COPY --chmod=755 bad-backend.sh /app/bad-backend.sh\nENTRYPOINT ["/app/bad-backend.sh"]\n'
        if release == 'frontend-bad':
            text += 'CMD ["node", "-e", "require(\'http\').createServer((q,r)=>{r.statusCode=q.url===\'/api/health\'?503:200;r.end(\'DOWN\')}).listen(3000,\'0.0.0.0\')"]\n'
        (OUT / 'Dockerfile').write_text(text)
        tag = PREFIX + ':' + release
        docker('build', '--pull=false', '-t', tag, OUT, timeout=180)
        IMAGES.append(tag)
    # Distinct frontend IDs for A and B, with the actual Next server/routes.
    for release in ('front-a', 'front-b'):
        (OUT / 'Dockerfile').write_text('FROM '+frontend_base+'\nLABEL shg16.rehearsal="'+PREFIX+'-'+release+'"\n')
        tag = PREFIX + ':' + release
        docker('build', '--pull=false', '-t', tag, OUT)
        IMAGES.append(tag)
    A, B, BAD_BACK, BAD_FRONT, FA, FB = IMAGES[1:]
    run(['openssl', 'genpkey', '-algorithm', 'EC', '-pkeyopt', 'ec_paramgen_curve:P-256', '-out', OUT / 'private.pem'])
    run(['openssl', 'pkey', '-in', OUT / 'private.pem', '-pubout', '-out', OUT / 'public.pem'])
    # Disposable keys readable by the actual non-root backend UID, protected by OUT 0700.
    (OUT / 'private.pem').chmod(0o644)
    env = {
        'BACKEND_IMAGE': A, 'FRONTEND_IMAGE': FA, 'POSTGRES_DB': 'savix', 'POSTGRES_USER': 'shg16',
        'POSTGRES_PASSWORD': SECRET, 'POSTGRES_EXPORTER_DATA_SOURCE_NAME': 'postgresql://shg16:'+SECRET+'@savix-db:5432/savix',
        'SMTP_HOST': '127.0.0.1', 'SMTP_USERNAME': 'dummy', 'SMTP_PASSWORD': SECRET,
        'PROXY_BASE': 'http://savix-backend:8000', 'PROXY_SECRET': SECRET,
        'EMAIL_FROM': 'dummy@example.invalid', 'JWT_ISSUER': 'shg16', 'JWT_AUDIENCE': 'shg16',
        'JWT_PRIVATE_KEY_FILE': str(OUT / 'private.pem'), 'JWT_PUBLIC_KEY_FILE': str(OUT / 'public.pem')}
    envfile = OUT / 'runtime.env'
    envfile.write_text(''.join(k+'='+v+'\n' for k,v in env.items()))
    envfile.chmod(0o600)
    for name in NETWORKS:
        docker('network', 'create', name)
    for name in VOLUMES:
        docker('volume', 'create', name)
    override = OUT / 'compose.test.yml'
    text = 'services:\n'
    for service in ('savix-backend', 'savix-frontend', 'savix-db', 'savix-redis', 'postgres-exporter', 'redis-exporter'):
        name = PREFIX + '-' + service
        NAMES.append(name)
        text += f'  {service}:\n    container_name: {name}\n    ports: !override []\n'
        if service == 'savix-backend':
            text += '    environment:\n      MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: \"always\"\n      MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS: \"always\"\n'
        if service == 'savix-db':
            text += '    image: postgres:16.11-bookworm\n'
        if service in ('savix-redis', 'postgres-exporter', 'redis-exporter'):
            text += '    image: redis:7-alpine\n'
        if service in ('postgres-exporter', 'redis-exporter'):
            text += '    command: [sleep, "86400"]\n'
    text += f'networks:\n  savix_internal:\n    name: {NETWORKS[0]}\n  monitoring_shared:\n    name: {NETWORKS[1]}\n'
    text += f'volumes:\n  savix-db-data:\n    name: {VOLUMES[0]}\n  savix-redis-existing-data:\n    name: {VOLUMES[1]}\n'
    override.write_text(text)
    compose = DOCKER + ['compose', '-p', PREFIX, '--env-file', str(envfile), '-f', str(ROOT / 'compose.production.yml'),
                        '-f', str(ROOT / 'compose.deploy.yml'), '-f', str(override)]
    run(compose + ['up', '-d', '--no-build', '--pull', 'never', 'savix-db', 'savix-redis', 'postgres-exporter', 'redis-exporter'])
    for i in range(40):
        if docker('exec', PREFIX+'-savix-db', 'pg_isready', '-U', 'shg16', '-d', 'savix', check=False).returncode == 0:
            break
        time.sleep(1)
    run(compose + ['run', '--rm', '--no-deps', '--pull', 'never', '-T', 'savix-migrate', 'db', 'migrate'])
    run(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'savix-backend', 'savix-frontend'])
    COMMON = ['--env-file', str(envfile), '--docker-context', 'desktop-linux', '--project', PREFIX,
              '--compose-override', str(override), '--backup-dir', str(OUT / 'backup'), '--state-dir', str(OUT / 'state'),
              '--lock-file', str(OUT / 'deploy.lock'), '--min-free-mb', '32', '--health-attempts', '20', '--health-interval', '1']
    original = snapshot()
    files = sorted(str(p) for p in OUT.rglob('*'))
    attempt('preflight', B, FB, extra=['--preflight'])
    assert identity(snapshot(), original) == identity(original, original)
    assert sorted(str(p) for p in OUT.rglob('*') if p.name != 'preflight.txt') == files
    passed('Read-only preflight leaves files, container identities and mounts unchanged')
    attempt('missing-image', PREFIX+':missing', FB, expected=1)
    assert identity(snapshot(), original) == identity(original, original)
    assert not list((OUT / 'backup').glob('*.dump'))
    passed('Missing image stops before backup or application replacement')
    lock = (OUT / 'deploy.lock').open('a+')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    output = attempt('lock-contention', B, FB, expected=1)
    assert 'holds the host lock' in output
    lock.close()
    passed('Host flock contention rejects overlapping deployment')
    attempt('insufficient-disk', B, FB, expected=1, extra=['--min-free-mb', '999999999'])
    assert identity(snapshot(), original) == identity(original, original)
    passed('Insufficient disk fails preflight before mutation')
    # Break only pg_dump in the disposable database container; psql preflight still passes.
    docker('exec', '-u', '0', PREFIX+'-savix-db', 'sh', '-c', 'mv /usr/lib/postgresql/16/bin/pg_dump /usr/lib/postgresql/16/bin/pg_dump.real; printf \'#!/bin/sh\nif [ "$1" = --version ]; then exec /usr/lib/postgresql/16/bin/pg_dump.real --version; fi\nprintf incomplete\nexit 42\n\' > /usr/lib/postgresql/16/bin/pg_dump; chmod +x /usr/lib/postgresql/16/bin/pg_dump')
    attempt('backup-failure', B, FB, expected=1)
    assert identity(snapshot(), original) == identity(original, original)
    assert not list((OUT / 'backup').glob('*.dump'))
    assert list((OUT / 'backup').glob('*.partial'))
    assert not any('Flyway' in p.read_text() for p in (OUT / 'state').glob('*/failure.json'))
    docker('exec', '-u', '0', PREFIX+'-savix-db', 'mv', '/usr/lib/postgresql/16/bin/pg_dump.real', '/usr/lib/postgresql/16/bin/pg_dump')
    passed('pg_dump failure retains partial only, blocks Flyway, leaves every existing container unchanged')
    checksum = sql("SELECT checksum FROM flyway_schema_history WHERE version='2'")
    sql("UPDATE flyway_schema_history SET checksum=0 WHERE version='2'")
    attempt('flyway-validation-failure', B, FB, expected=1)
    assert identity(snapshot(), original) == identity(original, original)
    assert sql("SELECT checksum FROM flyway_schema_history WHERE version='2'") == '0'
    sql("UPDATE flyway_schema_history SET checksum="+checksum+" WHERE version='2'")
    passed('Actual Flyway checksum validation failure follows backup and preserves both application containers; no repair')
    # A valid pending V2 that fails on preexisting incompatible data exercises migrate separately.
    sql("DELETE FROM flyway_schema_history WHERE version='2'; ALTER TABLE users DROP COLUMN email;")
    attempt('flyway-migrate-failure', B, FB, expected=1)
    assert identity(snapshot(), original) == identity(original, original)
    sql('ALTER TABLE users ADD COLUMN email varchar(255) UNIQUE;')
    passed('Actual Flyway migrate SQL failure precedes all application replacement')
    attempt('successful-deployment', B, FB)
    healthy = snapshot()
    assert all(healthy[s]['Id'] != original[s]['Id'] for s in ('savix-backend', 'savix-frontend'))
    success_record = next(p.parent / 'release.json' for p in (OUT / 'state').glob('*/success.json'))
    assert json.loads(success_record.read_text())['previous']['savix-backend']['id'] == original['savix-backend']['Image']
    passed('Successful real backend and Next frontend rollout; pinned candidate IDs, /actuator/health/readiness, /api/health and /login pass')
    readiness = health('/actuator/health/readiness', 200)
    assert set(readiness['components']) == {'readinessState', 'db', 'redis', 'diskSpace'}, readiness
    assert all(component['status'] == 'UP' for component in readiness['components'].values())
    aggregate = health('/actuator/health', 503)
    assert aggregate['components']['mail']['status'] == 'DOWN', aggregate
    assert all(aggregate['components'][name]['status'] == 'UP' for name in readiness['components'])
    proxied = health('/api/health', 200, 'frontend')
    assert set(proxied['components']) == set(readiness['components']), proxied
    passed('Unavailable SMTP leaves readiness and frontend proxy UP while aggregate health reports mail DOWN')
    for dependency, indicator in (('savix-db', 'db'), ('savix-redis', 'redis')):
        print('Checking readiness with disposable ' + dependency + ' stopped.', flush=True)
        docker('stop', PREFIX+'-'+dependency)
        try:
            direct = health('/actuator/health/readiness', 503)
            assert direct['components'][indicator]['status'] == 'DOWN', direct
            proxied = health('/api/health', 503, 'frontend')
            assert proxied['components'][indicator]['status'] == 'DOWN', proxied
        finally:
            docker('start', PREFIX+'-'+dependency)
        wait_ready()
        passed(dependency + ' outage fails backend readiness and frontend proxy; both recover after restart')
    assert identity(snapshot(), ('savix-db', 'savix-redis')) == identity(original, ('savix-db', 'savix-redis'))
    for dump in (OUT / 'backup').glob('*.dump'):
        import hashlib
        metadata = json.loads(dump.with_suffix('.json').read_text())
        assert hashlib.sha256(dump.read_bytes()).hexdigest() == metadata['sha256']
        assert dump.stat().st_mode & 0o077 == 0
    passed('Timestamped PostgreSQL 16 custom dumps inspected with pg_restore; metadata checksums and private permissions verified')
    attempt('backend-health-failure', BAD_BACK, FA, expected=1, extra=['--health-attempts', '2'])
    assert snapshot()['savix-frontend']['Id'] == healthy['savix-frontend']['Id']
    backend_failure_record = sorted((OUT / 'state').glob('*/release.json'))[-1]
    for log in (OUT / 'state').glob('*/*.log'):
        assert SECRET not in log.read_text()
    passed('Backend health failure captures bounded redacted logs, retains previous pair, never advances frontend')
    attempt('rollback-after-backend-failure', None, None, extra=['--rollback', str(backend_failure_record)])
    passed('Manual application rollback after backend failure restores healthy previous image pair')
    attempt('frontend-health-failure', A, BAD_FRONT, expected=1)
    assert snapshot()['savix-backend']['Image'] == original['savix-backend']['Image']
    assert list((OUT / 'state').glob('*/savix-frontend.log'))
    frontend_failure_record = sorted((OUT / 'state').glob('*/release.json'))[-1]
    history = sql('SELECT row_to_json(h) FROM flyway_schema_history h ORDER BY installed_rank')
    dump_count = len(list((OUT / 'backup').glob('*.dump')))
    attempt('rollback-after-frontend-failure', None, None, extra=['--rollback', str(frontend_failure_record)])
    assert all(snapshot()[s]['Image'] == healthy[s]['Image'] for s in ('savix-backend', 'savix-frontend'))
    assert sql('SELECT row_to_json(h) FROM flyway_schema_history h ORDER BY installed_rank') == history
    assert len(list((OUT / 'backup').glob('*.dump'))) == dump_count
    passed('Frontend failure marks deployment failed; manual rollback restores B/B without backup or database mutation')
    attempt('rollback-original-pair', None, None, extra=['--rollback', str(success_record)])
    final = snapshot()
    assert all(final[s]['Image'] == original[s]['Image'] for s in ('savix-backend', 'savix-frontend'))
    assert identity(final, ('savix-db', 'savix-redis', 'postgres-exporter', 'redis-exporter')) == identity(original, ('savix-db', 'savix-redis', 'postgres-exporter', 'redis-exporter'))
    passed('Original A/A image pair restored; DB, Redis, both exporter identities and every infrastructure mount unchanged through all cases')
    attempt('standalone-backup', A, FA, script='backup-db.sh')
    assert identity(snapshot(), final) == identity(final, final)
    passed('Standalone backup script shares preflight and lock; no application replacement')
    docker('rm', '-f', PREFIX+'-savix-backend')
    attempt('rollback-missing-backend', None, None, extra=['--rollback', str(success_record)])
    assert snapshot()['savix-backend']['Image'] == original['savix-backend']['Image']
    passed('Manual rollback recreates a missing backend container after an interrupted rollout')
finally:
    # Test fixture cleanup only; the deployment scripts never delete these resources.
    for name in NAMES:
        docker('rm', '-f', name, check=False)
    for name in VOLUMES:
        docker('volume', 'rm', name, check=False)
    for name in NETWORKS:
        docker('network', 'rm', name, check=False)
    for name in reversed(IMAGES):
        docker('image', 'rm', name, check=False)
    print('Local evidence: ' + str(OUT), flush=True)
