import { config } from './config.mjs';
import { verifyToken } from './security.mjs';

export function httpError(status, message, code) {
  return Object.assign(new Error(message), { status, code });
}

export async function readBody(req, limit = config.maxBodyBytes) {
  let size = 0; const parts = [];
  for await (const chunk of req) { size += chunk.length; if (size > limit) throw httpError(413, 'Request too large'); parts.push(chunk); }
  return Buffer.concat(parts);
}

export async function jsonBody(req) {
  const raw = await readBody(req);
  if (!raw.length) return {};
  let value;
  try { value = JSON.parse(raw.toString('utf8')); } catch { throw httpError(400, 'Invalid JSON'); }
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw httpError(400, 'JSON body must be an object');
  return value;
}

export function send(res, status, data, headers = {}) { res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...headers }); res.end(JSON.stringify(data)); }

export function route(pathname, pattern) {
  const p = pathname.split('/').filter(Boolean), q = pattern.split('/').filter(Boolean); if (p.length !== q.length) return null;
  const params = {};
  for (let i = 0; i < p.length; i++) {
    if (q[i].startsWith(':')) { try { params[q[i].slice(1)] = decodeURIComponent(p[i]); } catch { return null; } }
    else if (p[i] !== q[i]) return null;
  }
  return params;
}

export function auth(req, store) {
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, ''); const payload = verifyToken(token);
  const session = store.find('sessions', x => x.id === payload.sid && !x.revokedAt); if (!session) throw httpError(401, 'Session revoked');
  const user = store.find('users', x => x.id === payload.sub && !x.deletedAt); if (!user) throw httpError(401, 'Account unavailable');
  return payload;
}

export function sse(res, event, data) { if (!res.writableEnded && !res.destroyed) res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`); }

export function clientIp(req) {
  if (config.trustProxy) { const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim(); if (forwarded) return forwarded; }
  return req.socket.remoteAddress || 'unknown';
}
