#!/usr/bin/env node
// Run inside the current frontend runtime image with --network none.
// The real Next server talks only to this disposable loopback backend.
const assert = require('node:assert/strict');
const http = require('node:http');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { setTimeout: delay } = require('node:timers/promises');

async function waitFor(condition, timeout = 1500) {
  const deadline = performance.now() + timeout;
  while (!condition()) {
    assert.ok(performance.now() < deadline, 'Upstream request did not settle');
    await delay(20);
  }
}

async function main() {
  let mode = 'healthy';
  const pending = new Set();
  const backend = http.createServer((req, res) => {
    assert.equal(req.url, '/actuator/health/readiness');
    pending.add(res);
    res.on('close', () => pending.delete(res));
    if (mode === 'stall') return; // Accept the request, never send headers.
    if (mode === 'stall-body') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.write('{'); // Send headers but never finish the JSON body.
      return;
    }
    res.writeHead(mode === 'down' ? 503 : 200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: mode === 'down' ? 'DOWN' : 'UP' }));
  });
  backend.listen(0, '127.0.0.1');
  await once(backend, 'listening');
  const app = spawn(process.execPath, ['/app/server.js'], {
    env: { ...process.env, HOSTNAME: '127.0.0.1', PORT: '3000',
      PROXY_BASE: `http://127.0.0.1:${backend.address().port}` },
    stdio: 'ignore',
  });
  const appExited = once(app, 'exit');
  const endpoint = 'http://127.0.0.1:3000/api/health';
  async function check(status, body, stalled = false) {
    const start = performance.now();
    // Independent outer bound makes a broken handler fail rather than hang.
    const response = await fetch(endpoint, { signal: AbortSignal.timeout(6000) });
    assert.equal(response.status, status);
    assert.deepEqual(await response.json(), body);
    const elapsed = performance.now() - start;
    assert.ok(elapsed < 5000, `Health exceeded deployment probe budget: ${elapsed}ms`);
    if (stalled) assert.ok(elapsed >= 3500, `Backend did not stall until deadline: ${elapsed}ms`);
    await waitFor(() => pending.size === 0);
    console.log(`PASS: ${mode}: HTTP ${status}, ${body.status}, ${Math.round(elapsed)}ms; no pending upstream request`);
  }
  try {
    let ready = false;
    for (let attempt = 0; attempt < 100; attempt++) {
      try {
        const response = await fetch('http://127.0.0.1:3000/login', { signal: AbortSignal.timeout(1000) });
        await response.arrayBuffer();
        if (response.status === 200) { ready = true; break; }
      } catch { /* Wait only for this disposable Next server to start. */ }
      await delay(100);
    }
    assert.ok(ready, 'Frontend did not start');
    await check(200, { status: 'UP' });
    mode = 'down';
    await check(503, { status: 'DOWN' });
    for (mode of ['stall', 'stall-body']) {
      await check(503, { status: 'DOWN' }, true);
    }
    mode = 'stall';
    const controller = new AbortController();
    const abandoned = fetch(endpoint, { signal: controller.signal }).then(
      () => assert.fail('Abandoned health request unexpectedly completed'),
      error => assert.equal(error.name, 'AbortError'),
    );
    await waitFor(() => pending.size === 1);
    const canceledAt = performance.now();
    controller.abort();
    await abandoned;
    // Must close before the four-second timeout, proving cancellation propagation.
    await waitFor(() => pending.size === 0);
    console.log(`PASS: caller cancellation closed upstream in ${Math.round(performance.now() - canceledAt)}ms`);
    backend.closeAllConnections();
    await new Promise(resolve => backend.close(resolve));
    mode = 'connection-refused';
    await check(503, { status: 'DOWN' });
  } finally {
    backend.closeAllConnections();
    backend.close();
    app.kill('SIGKILL');
    await appExited;
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
