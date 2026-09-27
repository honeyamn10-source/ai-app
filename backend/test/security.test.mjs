import test from 'node:test';
import assert from 'node:assert/strict';
import { assertSafeRemoteUrl, decryptSecret, encryptSecret, hashPassword, isPrivateAddress, maskSecret, signToken, verifyPassword, verifyToken } from '../src/security.mjs';

test('secret encryption binds ciphertext to context', () => {
  const value = encryptSecret('sk-sensitive', 'account:1');
  assert.notEqual(value, 'sk-sensitive'); assert.equal(decryptSecret(value, 'account:1'), 'sk-sensitive');
  assert.throws(() => decryptSecret(value, 'account:2'));
});
test('password hashes verify without storing plaintext', () => { const hash = hashPassword('correct-horse-battery'); assert.ok(!hash.includes('correct-horse')); assert.equal(verifyPassword('correct-horse-battery', hash), true); assert.equal(verifyPassword('wrong-password', hash), false); });
test('weak passwords are a client error, missing hashes never verify', () => {
  assert.throws(() => hashPassword('short'), { status: 400 }); assert.throws(() => hashPassword(undefined), { status: 400 });
  assert.equal(verifyPassword('anything-at-all', null), false); assert.equal(verifyPassword('anything-at-all', 'not-a-hash'), false);
});
test('tokens reject wrong type, tampering and expiry with 401', () => {
  const token = signToken({ sub: 'u1', sid: 's1', type: 'access' }, 30); assert.equal(verifyToken(token).sub, 'u1');
  assert.throws(() => verifyToken(token, 'refresh'), { status: 401 });
  assert.throws(() => verifyToken(signToken({ sub: 'u1', sid: 's1', type: 'access' }, -1)), { status: 401 });
  const [h, , s] = token.split('.'); assert.throws(() => verifyToken(`${h}.${Buffer.from('{"sub":"admin","type":"access","exp":9999999999}').toString('base64url')}.${s}`), { status: 401 });
});
test('secret masking reveals only suffix', () => { assert.equal(maskSecret('abcdefgh1234'), '••••••••1234'); assert.equal(maskSecret('short'), '••••••••'); });
test('private and special addresses are detected', () => {
  for (const ip of ['127.0.0.1', '10.0.0.8', '172.20.1.1', '192.168.0.1', '169.254.169.254', '100.64.0.1', '0.0.0.0', '::1', 'fd00::1', 'fe80::1', '::ffff:127.0.0.1', 'not-an-ip']) assert.equal(isPrivateAddress(ip), true, ip);
  for (const ip of ['8.8.8.8', '1.1.1.1', '2606:4700:4700::1111', '172.32.0.1']) assert.equal(isPrivateAddress(ip), false, ip);
});
test('remote URL checks resolve DNS so rebinding-style hostnames are blocked', async () => {
  const resolveTo = address => async () => [{ address }];
  await assert.rejects(assertSafeRemoteUrl('https://internal.example.com/', { resolve: resolveTo('10.0.0.5') }), { status: 400 });
  await assert.rejects(assertSafeRemoteUrl('http://example.com/'), { status: 400 });
  await assert.rejects(assertSafeRemoteUrl('https://user:pass@example.com/'), { status: 400 });
  assert.equal((await assertSafeRemoteUrl('https://example.com/a', { resolve: resolveTo('93.184.216.34') })).href, 'https://example.com/a');
});
