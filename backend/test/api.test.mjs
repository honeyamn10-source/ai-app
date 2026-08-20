import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { server } from '../src/server.mjs';

let base;
test.before(async () => { server.listen(0, '127.0.0.1'); await once(server, 'listening'); base = `http://127.0.0.1:${server.address().port}`; });
test.after(() => server.close());
test('health and catalog are public', async () => { const health = await fetch(`${base}/health`).then(r => r.json()); assert.equal(health.status, 'ok'); const catalog = await fetch(`${base}/v1/models/catalog`).then(r => r.json()); assert.ok(catalog.providers.length >= 8); });
test('register, authenticate, create project and delete account', async () => {
  const email = `test-${Date.now()}@example.com`;
  const registration = await fetch(`${base}/v1/auth/register`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, name: 'Test User', password: 'correct-horse-battery' }) });
  assert.equal(registration.status, 201); const session = await registration.json();
  const headers = { authorization: `Bearer ${session.accessToken}`, 'content-type': 'application/json' };
  const project = await fetch(`${base}/v1/projects`, { method: 'POST', headers, body: JSON.stringify({ name: 'Research' }) }); assert.equal(project.status, 201);
  const providers = await fetch(`${base}/v1/providers`, { headers }).then(r => r.json()); assert.deepEqual(providers.items, []);
  const deletion = await fetch(`${base}/v1/me`, { method: 'DELETE', headers }); assert.equal(deletion.status, 200);
});

