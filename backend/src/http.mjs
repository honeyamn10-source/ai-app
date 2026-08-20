import { config } from './config.mjs';
import { verifyToken } from './security.mjs';

export async function jsonBody(req) {
  let size = 0; const parts = [];
  for await (const chunk of req) { size += chunk.length; if (size > config.maxBodyBytes) throw Object.assign(new Error('Request too large'), { status: 413 }); parts.push(chunk); }
  if (!parts.length) return {};
  try { return JSON.parse(Buffer.concat(parts).toString('utf8')); } catch { throw Object.assign(new Error('Invalid JSON'), { status: 400 }); }
}
export function send(res, status, data, headers = {}) { res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...headers }); res.end(JSON.stringify(data)); }
export function route(pathname, pattern) {
  const p = pathname.split('/').filter(Boolean), q = pattern.split('/').filter(Boolean); if (p.length !== q.length) return null;
  const params = {}; for (let i = 0; i < p.length; i++) { if (q[i].startsWith(':')) params[q[i].slice(1)] = decodeURIComponent(p[i]); else if (p[i] !== q[i]) return null; } return params;
}
export function auth(req, store) {
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, ''); const payload = verifyToken(token);
  const session = store.find('sessions', x => x.id === payload.sid && !x.revokedAt); if (!session) throw Object.assign(new Error('Session revoked'), { status: 401 });
  return payload;
}
export function sse(res, event, data) { res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`); }

