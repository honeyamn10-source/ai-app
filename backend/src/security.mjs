import { BlockList, isIP } from 'node:net';
import { lookup } from 'node:dns/promises';
import { createCipheriv, createDecipheriv, createHash, createHmac, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';
import { config } from './config.mjs';

const b64u = value => Buffer.from(value).toString('base64url');
const parse64u = value => Buffer.from(value, 'base64url');
const failure = (status, message) => Object.assign(new Error(message), { status });

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
  const value = String(password ?? '');
  if (value.length < 10) throw failure(400, 'Password must contain at least 10 characters');
  if (value.length > 1024) throw failure(400, 'Password is too long');
  const salt = randomBytes(16);
  return `${b64u(salt)}.${b64u(scryptSync(value, salt, 64))}`;
}

// Used when no account matches so failed logins take the same time either way.
const decoyHash = `${b64u(randomBytes(16))}.${b64u(randomBytes(64))}`;

export function verifyPassword(password, stored) {
  const [salt, hash] = String(stored || decoyHash).split('.').map(parse64u);
  if (!salt?.length || !hash?.length) return false;
  const candidate = scryptSync(String(password ?? ''), salt, 64);
  return candidate.length === hash.length && timingSafeEqual(candidate, hash) && Boolean(stored);
}

/** Fast keyed hash for high-entropy values (refresh tokens, purchase tokens). */
export function hashToken(value) { return createHmac('sha256', config.tokenSecret).update(String(value)).digest('base64url'); }

export function safeEqual(a, b) {
  const x = createHash('sha256').update(String(a)).digest(); const y = createHash('sha256').update(String(b)).digest();
  return timingSafeEqual(x, y);
}

export function signToken(payload, expiresIn) {
  const header = b64u(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = b64u(JSON.stringify({ ...payload, exp: Math.floor(Date.now() / 1000) + expiresIn }));
  const signature = createHmac('sha256', config.tokenSecret).update(`${header}.${body}`).digest('base64url');
  return `${header}.${body}.${signature}`;
}

export function verifyToken(token, expectedType = 'access') {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) throw failure(401, 'Invalid token');
  const expected = createHmac('sha256', config.tokenSecret).update(`${parts[0]}.${parts[1]}`).digest();
  const actual = parse64u(parts[2]);
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) throw failure(401, 'Invalid token');
  let payload;
  try { payload = JSON.parse(parse64u(parts[1]).toString('utf8')); } catch { throw failure(401, 'Invalid token'); }
  if (!(payload.exp >= Math.floor(Date.now() / 1000)) || payload.type !== expectedType) throw failure(401, 'Expired or invalid token');
  return payload;
}

export function maskSecret(secret) {
  const value = String(secret);
  return `••••••••${value.length > 8 ? value.slice(-4) : ''}`;
}

export function sanitizeText(value, max = 20000) {
  return String(value ?? '').replaceAll('\u0000', '').slice(0, max);
}

const privateRanges = new BlockList();
for (const [net, prefix] of [['0.0.0.0', 8], ['10.0.0.0', 8], ['100.64.0.0', 10], ['127.0.0.0', 8], ['169.254.0.0', 16], ['172.16.0.0', 12], ['192.0.0.0', 24], ['192.0.2.0', 24], ['192.168.0.0', 16], ['198.18.0.0', 15], ['198.51.100.0', 24], ['203.0.113.0', 24], ['224.0.0.0', 4], ['240.0.0.0', 4]]) privateRanges.addSubnet(net, prefix, 'ipv4');
for (const [net, prefix] of [['::', 128], ['::1', 128], ['fc00::', 7], ['fe80::', 10], ['ff00::', 8], ['2001:db8::', 32], ['64:ff9b::', 96]]) privateRanges.addSubnet(net, prefix, 'ipv6');

export function isPrivateAddress(address) {
  let ip = String(address).replace(/^\[|\]$/g, '');
  const mapped = ip.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/i); if (mapped) ip = mapped[1];
  const family = isIP(ip);
  if (!family) return true;
  return privateRanges.check(ip, family === 6 ? 'ipv6' : 'ipv4');
}

/** Rejects non-HTTPS URLs and any host that is, or resolves to, a private/loopback/link-local address. */
export async function assertSafeRemoteUrl(input, { resolve = lookup } = {}) {
  let url;
  try { url = new URL(input); } catch { throw failure(400, 'Invalid URL'); }
  if (url.protocol !== 'https:') throw failure(400, 'Only HTTPS URLs are allowed');
  if (url.username || url.password) throw failure(400, 'URLs with embedded credentials are not allowed');
  const host = url.hostname.toLowerCase().replace(/^\[|\]$/g, '');
  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') || host.endsWith('.internal')) throw failure(400, 'Private network targets are blocked');
  const addresses = isIP(host) ? [{ address: host }] : await resolve(host, { all: true, verbatim: true }).catch(() => { throw failure(400, 'Host could not be resolved'); });
  if (!addresses.length || addresses.some(x => isPrivateAddress(x.address))) throw failure(400, 'Private network targets are blocked');
  return url;
}
