import { createCipheriv, createDecipheriv, createHmac, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import { config } from './config.mjs';

const b64u = value => Buffer.from(value).toString('base64url');
const parse64u = value => Buffer.from(value, 'base64url');

export function encryptSecret(plainText, context = 'provider-key') {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', config.masterKey, iv);
  cipher.setAAD(Buffer.from(context));
  const encrypted = Buffer.concat([cipher.update(String(plainText), 'utf8'), cipher.final()]);
  return `${b64u(iv)}.${b64u(cipher.getAuthTag())}.${b64u(encrypted)}`;
}

export function decryptSecret(envelope, context = 'provider-key') {
  const [iv, tag, encrypted] = String(envelope).split('.').map(parse64u);
  const decipher = createDecipheriv('aes-256-gcm', config.masterKey, iv);
  decipher.setAAD(Buffer.from(context));
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
}

export function hashPassword(password) {
  if (String(password).length < 10) throw new Error('Password must contain at least 10 characters');
  const salt = randomBytes(16);
  return `${b64u(salt)}.${b64u(scryptSync(password, salt, 64))}`;
}

export function verifyPassword(password, stored) {
  const [salt, hash] = stored.split('.').map(parse64u);
  const candidate = scryptSync(password, salt, 64);
  return candidate.length === hash.length && timingSafeEqual(candidate, hash);
}

export function signToken(payload, expiresIn) {
  const header = b64u(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64u(JSON.stringify({ ...payload, exp: Math.floor(Date.now() / 1000) + expiresIn }));
  const signature = createHmac('sha256', config.tokenSecret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${signature}`;
}

export function verifyToken(token, expectedType = 'access') {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) throw new Error('Invalid token');
  const expected = createHmac('sha256', config.tokenSecret).update(`${parts[0]}.${parts[1]}`).digest();
  const actual = parse64u(parts[2]);
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) throw new Error('Invalid token');
  const payload = JSON.parse(parse64u(parts[1]).toString('utf8'));
  if (payload.exp < Math.floor(Date.now() / 1000) || payload.type !== expectedType) throw new Error('Expired or invalid token');
  return payload;
}

export function maskSecret(secret) {
  const value = String(secret);
  return `••••••••${value.slice(-4)}`;
}

export function sanitizeText(value, max = 20000) {
  return String(value ?? '').replaceAll('\u0000', '').slice(0, max);
}

export function assertSafeRemoteUrl(input) {
  const url = new URL(input);
  if (url.protocol !== 'https:') throw new Error('Only HTTPS URLs are allowed');
  const host = url.hostname.toLowerCase();
  if (host === 'localhost' || host.endsWith('.local') || /^127\.|^10\.|^192\.168\.|^169\.254\./.test(host)) {
    throw new Error('Private network targets are blocked');
  }
  return url;
}

