import test from 'node:test';
import assert from 'node:assert/strict';
import { decryptSecret, encryptSecret, hashPassword, maskSecret, signToken, verifyPassword, verifyToken } from '../src/security.mjs';

test('secret encryption binds ciphertext to context', () => {
  const value = encryptSecret('sk-sensitive', 'account:1');
  assert.notEqual(value, 'sk-sensitive'); assert.equal(decryptSecret(value, 'account:1'), 'sk-sensitive');
  assert.throws(() => decryptSecret(value, 'account:2'));
});
test('password hashes verify without storing plaintext', () => { const hash = hashPassword('correct-horse-battery'); assert.ok(!hash.includes('correct-horse')); assert.equal(verifyPassword('correct-horse-battery', hash), true); assert.equal(verifyPassword('wrong-password', hash), false); });
test('tokens reject wrong type and expired tokens', () => { const token = signToken({ sub: 'u1', sid: 's1', type: 'access' }, 30); assert.equal(verifyToken(token).sub, 'u1'); assert.throws(() => verifyToken(token, 'refresh')); const expired = signToken({ sub: 'u1', sid: 's1', type: 'access' }, -1); assert.throws(() => verifyToken(expired)); });
test('secret masking reveals only suffix', () => assert.equal(maskSecret('abcdefgh1234'), '••••••••1234'));

