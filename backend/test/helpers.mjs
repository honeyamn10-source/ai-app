import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/** Points the store at a throwaway file; call before importing any src module. */
export function isolateStore() {
  process.env.BYAK_AUTH_RATE_LIMIT = '1000';
  process.env.BYAK_DATA_FILE = join(mkdtempSync(join(tmpdir(), 'byak-test-')), 'store.json');
  return process.env.BYAK_DATA_FILE;
}

/** Builds a streaming SSE Response from a list of data payloads. */
export function sseResponse(events, { status = 200 } = {}) {
  const body = events.map(e => `data: ${typeof e === 'string' ? e : JSON.stringify(e)}\n\n`).join('');
  return new Response(new ReadableStream({ start(controller) { const bytes = new TextEncoder().encode(body); for (let i = 0; i < bytes.length; i += 17) controller.enqueue(bytes.slice(i, i + 17)); controller.close(); } }), { status, headers: { 'content-type': 'text/event-stream' } });
}

/** Routes fetch calls whose URL starts with a registered prefix to a fake; everything else (the local test server) goes to real fetch. */
export function mockFetch() {
  const real = globalThis.fetch; const routes = []; const calls = [];
  globalThis.fetch = async (input, init = {}) => {
    const url = String(input instanceof URL ? input.href : input.url || input);
    const match = routes.find(r => url.startsWith(r.prefix));
    if (!match) return real(input, init);
    calls.push({ url, init, body: init.body ? JSON.parse(String(init.body)) : null });
    return match.handler(url, init);
  };
  return { on(prefix, handler) { routes.unshift({ prefix, handler }); }, calls, restore() { globalThis.fetch = real; } };
}

/** Parses an SSE HTTP response body into [{ event, data }]. */
export async function readSse(response) {
  const text = await response.text();
  return text.split('\n\n').filter(Boolean).map(block => { const event = block.match(/^event: (.*)$/m)?.[1]; const data = block.match(/^data: (.*)$/m)?.[1]; return { event, data: data ? JSON.parse(data) : null }; });
}
