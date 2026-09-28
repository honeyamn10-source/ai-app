import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { isolateStore, mockFetch, readSse, sseResponse } from './helpers.mjs';

isolateStore();
process.env.BRAVE_SEARCH_API_KEY = 'test-brave-key';
const { server } = await import('../src/server.mjs');
const fake = mockFetch();

let base;
test.before(async () => { server.listen(0, '127.0.0.1'); await once(server, 'listening'); base = `http://127.0.0.1:${server.address().port}`; });
test.after(() => { fake.restore(); server.close(); });

const json = (path, { method = 'GET', token, body, raw } = {}) => fetch(`${base}${path}`, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: raw ?? (body === undefined ? undefined : JSON.stringify(body)) });
let counter = 0;
async function account() {
  const response = await json('/v1/auth/register', { method: 'POST', body: { email: `user-${Date.now()}-${counter++}@example.com`, name: 'Test User', password: 'correct-horse-battery' } });
  assert.equal(response.status, 201); return response.json();
}

test('health and catalog are public', async () => {
  const health = await fetch(`${base}/health`).then(r => r.json()); assert.equal(health.status, 'ok');
  const catalog = await fetch(`${base}/v1/models/catalog`).then(r => r.json()); assert.ok(catalog.providers.length >= 8);
  assert.ok(catalog.providers.every(p => !('baseUrl' in p) || typeof p.baseUrl === 'string'));
});

test('bad input returns 4xx instead of 500', async () => {
  assert.equal((await json('/v1/auth/register', { method: 'POST', body: { email: 'short@example.com', password: 'short' } })).status, 400);
  assert.equal((await json('/v1/auth/login', { method: 'POST', raw: '{nope' })).status, 400);
  assert.equal((await json('/v1/auth/login', { method: 'POST', raw: 'null' })).status, 400);
  const { accessToken } = await account();
  assert.equal((await json('/v1/conversations/%E0%A4%A', { token: accessToken })).status, 404);
  assert.equal((await json('/v1/me', { token: 'garbage' })).status, 401);
});

test('register, authenticate, create project and delete account', async () => {
  const session = await account(); const token = session.accessToken;
  const project = await json('/v1/projects', { method: 'POST', token, body: { name: 'Research' } }); assert.equal(project.status, 201);
  const providers = await json('/v1/providers', { token }).then(r => r.json()); assert.deepEqual(providers.items, []);
  const exported = await json('/v1/me/export', { token }).then(r => r.json()); assert.equal(exported.projects.length, 1);
  const deletion = await json('/v1/me', { method: 'DELETE', token }); assert.equal(deletion.status, 200);
  assert.equal((await json('/v1/me', { token })).status, 401);
});

test('refresh rotates tokens and detects reuse of an old refresh token', async () => {
  const session = await account();
  const first = await json('/v1/auth/refresh', { method: 'POST', body: { refreshToken: session.refreshToken } }); assert.equal(first.status, 200);
  const rotated = await first.json(); assert.notEqual(rotated.refreshToken, session.refreshToken);
  assert.equal((await json('/v1/me', { token: rotated.accessToken })).status, 200);
  assert.equal((await json('/v1/auth/refresh', { method: 'POST', body: { refreshToken: session.refreshToken } })).status, 401);
  // Reuse revokes the whole session, including the newest tokens.
  assert.equal((await json('/v1/me', { token: rotated.accessToken })).status, 401);
});

test('logout revokes the session and login rejects wrong passwords', async () => {
  const session = await account();
  assert.equal((await json('/v1/auth/login', { method: 'POST', body: { email: session.user.email, password: 'wrong-password-123' } })).status, 401);
  assert.equal((await json('/v1/auth/logout', { method: 'POST', token: session.accessToken })).status, 200);
  assert.equal((await json('/v1/me', { token: session.accessToken })).status, 401);
});

test('provider base URLs cannot target private networks', async () => {
  const { accessToken: token } = await account();
  for (const baseUrl of ['http://example.com/v1', 'https://127.0.0.1/v1', 'https://10.1.2.3/v1', 'https://[::1]/v1', 'https://localhost/v1'])
    assert.equal((await json('/v1/providers', { method: 'POST', token, body: { provider: 'custom', apiKey: 'k-123456789', baseUrl } })).status, 400, baseUrl);
  const created = await json('/v1/providers', { method: 'POST', token, body: { provider: 'openai', apiKey: 'sk-test-1234567890', baseUrl: 'https://169.254.169.254/' } }).then(r => r.json());
  assert.equal(created.baseUrl, ''); // non-custom providers ignore base URL overrides
  assert.equal((await json('/v1/tools/url-reader', { method: 'POST', token, body: { url: 'https://192.168.1.1/' } })).status, 400);
});

test('streams OpenAI-compatible answers with RAG, project instructions and memory', async () => {
  const { accessToken: token } = await account();
  fake.on('https://api.openai.com/v1/chat/completions', () => sseResponse([{ choices: [{ delta: { content: 'Hello' } }] }, { choices: [{ delta: { content: ' world' }, finish_reason: 'stop' }] }, { choices: [], usage: { prompt_tokens: 12, completion_tokens: 3 } }, '[DONE]']));
  const provider = await json('/v1/providers', { method: 'POST', token, body: { provider: 'openai', apiKey: 'sk-test-1234567890', defaultModel: 'gpt-4.1-mini' } }).then(r => r.json());
  assert.equal(provider.maskedKey, '••••••••7890');
  const project = await json('/v1/projects', { method: 'POST', token, body: { name: 'Launch', instructions: 'Answer like a pirate.' } }).then(r => r.json());
  await json('/v1/files', { method: 'POST', token, body: { name: 'notes.md', mimeType: 'text/markdown', projectId: project.id, content: 'The launch codename is BLUEBIRD and ships in March.' } });
  await json('/v1/memory/settings', { method: 'PUT', token, body: { enabled: true } });
  await json('/v1/memory', { method: 'POST', token, body: { content: 'User prefers short answers' } });
  const conversation = await json('/v1/conversations', { method: 'POST', token, body: { projectId: project.id, providerId: provider.id } }).then(r => r.json());

  const events = await readSse(await json(`/v1/conversations/${conversation.id}/stream`, { method: 'POST', token, body: { content: 'What is the launch codename?' } }));
  assert.deepEqual(events.filter(e => e.event === 'content_delta').map(e => e.data.delta), ['Hello', ' world']);
  const complete = events.find(e => e.event === 'message_complete').data;
  assert.equal(complete.content, 'Hello world'); assert.equal(complete.citations[0].title, 'notes.md');

  const sent = fake.calls.at(-1).body; const system = sent.messages[0].content;
  assert.equal(sent.stream, true); assert.equal('temperature' in sent, false);
  assert.match(system, /pirate/); assert.match(system, /short answers/); assert.match(system, /BLUEBIRD/);
  assert.equal(fake.calls.at(-1).init.headers.authorization, 'Bearer sk-test-1234567890');

  const stored = await json(`/v1/conversations/${conversation.id}`, { token }).then(r => r.json());
  assert.equal(stored.title, 'What is the launch codename?'); assert.equal(stored.messages.length, 2);
  const usage = await json('/v1/usage', { token }).then(r => r.json()); assert.equal(usage.inputTokens, 12); assert.equal(usage.outputTokens, 3);

  fake.on('https://api.openai.com/v1/chat/completions', () => sseResponse([{ choices: [{ delta: { content: 'Second try' } }] }, '[DONE]']));
  const regenerated = await readSse(await json(`/v1/conversations/${conversation.id}/regenerate`, { method: 'POST', token, body: {} }));
  assert.equal(regenerated.find(e => e.event === 'message_complete').data.content, 'Second try');
  const after = await json(`/v1/conversations/${conversation.id}`, { token }).then(r => r.json());
  assert.deepEqual(after.messages.map(m => m.content), ['What is the launch codename?', 'Second try']);

  // Deleting the project keeps its conversations but unlinks them.
  await json(`/v1/projects/${project.id}`, { method: 'DELETE', token });
  assert.equal((await json(`/v1/conversations/${conversation.id}`, { token }).then(r => r.json())).projectId, null);
});

test('Anthropic requests omit temperature and pass the system prompt separately', async () => {
  const { accessToken: token } = await account();
  fake.on('https://api.anthropic.com/v1/messages', () => sseResponse([{ type: 'message_start', message: { usage: { input_tokens: 20 } } }, { type: 'content_block_delta', delta: { type: 'thinking_delta', thinking: '' } }, { type: 'content_block_delta', delta: { type: 'text_delta', text: 'Hi from Claude' } }, { type: 'message_delta', delta: { stop_reason: 'end_turn' }, usage: { output_tokens: 4 } }]));
  const provider = await json('/v1/providers', { method: 'POST', token, body: { provider: 'anthropic', apiKey: 'sk-ant-123456789' } }).then(r => r.json());
  assert.equal(provider.defaultModel, 'claude-opus-5');
  const conversation = await json('/v1/conversations', { method: 'POST', token, body: { providerId: provider.id } }).then(r => r.json());
  const response = await json(`/v1/conversations/${conversation.id}/messages`, { method: 'POST', token, body: { content: 'Hello' } });
  assert.equal(response.status, 201); assert.equal((await response.json()).content, 'Hi from Claude');
  const call = fake.calls.at(-1);
  assert.equal('temperature' in call.body, false); assert.match(call.body.system, /BYAK AI/);
  assert.deepEqual(call.body.messages, [{ role: 'user', content: 'Hello' }]);
  assert.equal(call.init.headers['x-api-key'], 'sk-ant-123456789'); assert.equal(call.init.headers['anthropic-version'], '2023-06-01');
});

test('Gemini keeps the key out of the URL and sends a system instruction', async () => {
  const { accessToken: token } = await account();
  fake.on('https://generativelanguage.googleapis.com/', () => sseResponse([{ candidates: [{ content: { parts: [{ text: 'Gemini says hi' }] }, finishReason: 'STOP' }], usageMetadata: { promptTokenCount: 5, candidatesTokenCount: 3 } }]));
  const provider = await json('/v1/providers', { method: 'POST', token, body: { provider: 'gemini', apiKey: 'AIza-secret-key-123' } }).then(r => r.json());
  const conversation = await json('/v1/conversations', { method: 'POST', token, body: { providerId: provider.id } }).then(r => r.json());
  const answer = await json(`/v1/conversations/${conversation.id}/messages`, { method: 'POST', token, body: { content: 'Hi' } }).then(r => r.json());
  assert.equal(answer.content, 'Gemini says hi');
  const call = fake.calls.at(-1);
  assert.ok(!call.url.includes('AIza')); assert.match(call.url, /:streamGenerateContent\?alt=sse$/);
  assert.equal(call.init.headers['x-goog-api-key'], 'AIza-secret-key-123'); assert.match(call.body.systemInstruction.parts[0].text, /BYAK AI/);
});

test('provider failures reach the client as a readable SSE error', async () => {
  const { accessToken: token } = await account();
  fake.on('https://api.groq.com/', () => new Response(JSON.stringify({ error: { message: 'Invalid API Key' } }), { status: 401 }));
  const provider = await json('/v1/providers', { method: 'POST', token, body: { provider: 'groq', apiKey: 'gsk-123456789' } }).then(r => r.json());
  const conversation = await json('/v1/conversations', { method: 'POST', token, body: { providerId: provider.id } }).then(r => r.json());
  const events = await readSse(await json(`/v1/conversations/${conversation.id}/stream`, { method: 'POST', token, body: { content: 'Hi' } }));
  const error = events.find(e => e.event === 'error').data;
  assert.match(error.message, /rejected your API key/); assert.match(error.message, /Invalid API Key/);
});

test('free plan limits are enforced with 402', async () => {
  const { accessToken: token } = await account();
  for (let i = 0; i < 3; i++) assert.equal((await json('/v1/projects', { method: 'POST', token, body: { name: `P${i}` } })).status, 201);
  const blocked = await json('/v1/projects', { method: 'POST', token, body: { name: 'P4' } });
  assert.equal(blocked.status, 402); assert.equal((await blocked.json()).error.code, 'plan_limit');
  const subscription = await json('/v1/subscription', { token }).then(r => r.json());
  assert.equal(subscription.plan, 'free'); assert.equal(subscription.billingAccountId.length > 20, true);
});

test('purchase verification explains missing server configuration', async () => {
  const { accessToken: token } = await account();
  const response = await json('/v1/billing/google/verify', { method: 'POST', token, body: { purchaseToken: 'token-123' } });
  assert.equal(response.status, 503); assert.match((await response.json()).error.message, /GOOGLE_PLAY_SERVICE_ACCOUNT_JSON/);
});

test('conversations can be searched, pinned and archived', async () => {
  const { accessToken: token } = await account();
  const a = await json('/v1/conversations', { method: 'POST', token, body: { title: 'Rust borrow checker' } }).then(r => r.json());
  await json('/v1/conversations', { method: 'POST', token, body: { title: 'Dinner ideas' } });
  const found = await json('/v1/conversations?q=borrow', { token }).then(r => r.json()); assert.deepEqual(found.items.map(x => x.id), [a.id]);
  await json(`/v1/conversations/${a.id}`, { method: 'PATCH', token, body: { archived: true } });
  assert.equal((await json('/v1/conversations', { token }).then(r => r.json())).items.length, 1);
  assert.equal((await json('/v1/conversations?archived=true', { token }).then(r => r.json())).items.length, 1);
  assert.equal((await json('/v1/conversations', { method: 'POST', token, body: { projectId: 'not-mine' } })).status, 400);
});

test('disconnecting mid-stream stops generation and keeps the partial answer', async () => {
  const { accessToken: token } = await account();
  let upstreamAborted = false;
  fake.on('https://api.deepseek.com/', (url, init) => {
    const encoder = new TextEncoder();
    return new Response(new ReadableStream({
      async start(controller) {
        init.signal.addEventListener('abort', () => { upstreamAborted = true; controller.error(new DOMException('aborted', 'AbortError')); });
        controller.enqueue(encoder.encode(`data: ${JSON.stringify({ choices: [{ delta: { content: 'Partial answer' } }] })}\n\n`));
      }
    }), { headers: { 'content-type': 'text/event-stream' } });
  });
  const provider = await json('/v1/providers', { method: 'POST', token, body: { provider: 'deepseek', apiKey: 'sk-deep-123456789' } }).then(r => r.json());
  const conversation = await json('/v1/conversations', { method: 'POST', token, body: { providerId: provider.id } }).then(r => r.json());
  const controller = new AbortController();
  const response = await fetch(`${base}/v1/conversations/${conversation.id}/stream`, { method: 'POST', signal: controller.signal, headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` }, body: JSON.stringify({ content: 'Write a long essay' }) });
  const reader = response.body.getReader(); let seen = '';
  while (!seen.includes('Partial answer')) seen += new TextDecoder().decode((await reader.read()).value);
  controller.abort();
  for (let i = 0; i < 50 && !upstreamAborted; i++) await new Promise(r => setTimeout(r, 20));
  assert.equal(upstreamAborted, true);
  await new Promise(r => setTimeout(r, 50));
  const stored = await json(`/v1/conversations/${conversation.id}`, { token }).then(r => r.json());
  assert.equal(stored.messages.at(-1).content, 'Partial answer'); assert.equal(stored.messages.at(-1).status, 'stopped');
});

const PNG = Buffer.from('89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000a49444154789c6360000000020001e221bc330000000049454e44ae426082', 'hex').toString('base64');
async function chatWith(provider, apiKey) {
  const { accessToken: token } = await account();
  const p = await json('/v1/providers', { method: 'POST', token, body: { provider, apiKey } }).then(r => r.json());
  const conversation = await json('/v1/conversations', { method: 'POST', token, body: { providerId: p.id } }).then(r => r.json());
  return { token, conversation };
}
const openaiOk = text => () => sseResponse([{ choices: [{ delta: { content: text } }] }, '[DONE]']);

test('images are sent to vision models and stored without leaking bytes in message lists', async () => {
  fake.on('https://api.openai.com/v1/chat/completions', openaiOk('A tiny image'));
  const { token, conversation } = await chatWith('openai', 'sk-test-1234567890');
  const events = await readSse(await json(`/v1/conversations/${conversation.id}/stream`, { method: 'POST', token, body: { content: 'What is this?', attachments: [{ type: 'image', mimeType: 'image/png', data: PNG }] } }));
  assert.equal(events.find(e => e.event === 'message_complete').data.content, 'A tiny image');
  const sent = fake.calls.at(-1).body.messages.at(-1);
  assert.equal(sent.content[0].text, 'What is this?'); assert.equal(sent.content[1].image_url.url, `data:image/png;base64,${PNG}`);

  const stored = await json(`/v1/conversations/${conversation.id}`, { token }).then(r => r.json());
  const userMessage = stored.messages[0];
  assert.deepEqual(userMessage.attachments, [{ index: 0, type: 'image', mimeType: 'image/png', size: Buffer.from(PNG, 'base64').length }]);
  assert.ok(!JSON.stringify(stored).includes(PNG));
  const image = await json(`/v1/conversations/${conversation.id}/messages/${userMessage.id}/attachments/0`, { token });
  assert.equal(image.headers.get('content-type'), 'image/png'); assert.equal(Buffer.from(await image.arrayBuffer()).toString('base64'), PNG);

  // Only the message being answered carries image bytes; older images become a short note.
  await readSse(await json(`/v1/conversations/${conversation.id}/stream`, { method: 'POST', token, body: { content: 'And now?' } }));
  const followUp = fake.calls.at(-1).body.messages;
  assert.match(followUp[1].content, /1 image\(s\) were attached/); assert.equal(followUp.at(-1).content, 'And now?');
});

test('image formats for Anthropic and validation errors', async () => {
  fake.on('https://api.anthropic.com/v1/messages', () => sseResponse([{ type: 'content_block_delta', delta: { type: 'text_delta', text: 'ok' } }]));
  const { token, conversation } = await chatWith('anthropic', 'sk-ant-123456789');
  await json(`/v1/conversations/${conversation.id}/messages`, { method: 'POST', token, body: { content: '', attachments: [{ type: 'image', mimeType: 'image/png', data: PNG }] } });
  const content = fake.calls.at(-1).body.messages[0].content;
  assert.deepEqual(content[0], { type: 'image', source: { type: 'base64', media_type: 'image/png', data: PNG } }); assert.equal(content[1].type, 'text');
  const post = body => json(`/v1/conversations/${conversation.id}/messages`, { method: 'POST', token, body });
  assert.equal((await post({ content: 'x', attachments: [{ type: 'image', mimeType: 'image/svg+xml', data: PNG }] })).status, 415);
  assert.equal((await post({ content: 'x', attachments: Array(5).fill({ type: 'image', mimeType: 'image/png', data: PNG }) })).status, 400);
  assert.equal((await post({ content: 'x', attachments: 'nope' })).status, 400);
});

test('web search results and links become cited context', async () => {
  fake.on('https://api.search.brave.com/', () => Response.json({ web: { results: [{ title: 'Launch news', url: 'https://news.example/launch', description: 'BYAK 2.0 launched today' }] } }));
  fake.on('https://api.openai.com/v1/chat/completions', openaiOk('It launched today [Web source 1]'));
  const { token, conversation } = await chatWith('openai', 'sk-test-1234567890');
  const events = await readSse(await json(`/v1/conversations/${conversation.id}/stream`, { method: 'POST', token, body: { content: 'Did BYAK launch? see https://10.0.0.1/internal', webSearch: true } }));
  assert.ok(events.some(e => e.event === 'status' && /Searching the web/.test(e.data.message)));
  const complete = events.find(e => e.event === 'message_complete').data;
  assert.deepEqual(complete.citations, [{ id: 1, kind: 'web', title: 'Launch news', url: 'https://news.example/launch' }]);
  const system = fake.calls.at(-1).body.messages[0].content;
  assert.match(system, /\[Web source 1: Launch news\]\(https:\/\/news.example\/launch\)/);
  assert.match(system, /Could not read https:\/\/10\.0\.0\.1\/internal: Private network targets are blocked/);
});

test('editing a message replaces it and everything after it', async () => {
  let n = 0; fake.on('https://api.openai.com/v1/chat/completions', () => sseResponse([{ choices: [{ delta: { content: `answer ${++n}` } }] }, '[DONE]']));
  const { token, conversation } = await chatWith('openai', 'sk-test-1234567890');
  await readSse(await json(`/v1/conversations/${conversation.id}/stream`, { method: 'POST', token, body: { content: 'first question' } }));
  await readSse(await json(`/v1/conversations/${conversation.id}/stream`, { method: 'POST', token, body: { content: 'second question' } }));
  const before = await json(`/v1/conversations/${conversation.id}`, { token }).then(r => r.json());
  const events = await readSse(await json(`/v1/conversations/${conversation.id}/edit`, { method: 'POST', token, body: { messageId: before.messages[0].id, content: 'first question, edited' } }));
  assert.equal(events.find(e => e.event === 'message_complete').data.content, 'answer 3');
  const after = await json(`/v1/conversations/${conversation.id}`, { token }).then(r => r.json());
  assert.deepEqual(after.messages.map(m => m.content), ['first question, edited', 'answer 3']);
  assert.equal((await json(`/v1/conversations/${conversation.id}/edit`, { method: 'POST', token, body: { messageId: after.messages[1].id, content: 'x' } })).status, 404);
});

test('prompt library CRUD with built-in templates', async () => {
  const { accessToken: token } = await account();
  const initial = await json('/v1/prompts', { token }).then(r => r.json());
  assert.deepEqual(initial.items, []); assert.ok(initial.templates.length >= 5); assert.ok(initial.templates.every(t => t.content.includes('{{input}}')));
  const created = await json('/v1/prompts', { method: 'POST', token, body: { title: 'Standup', content: 'Turn these notes into a standup update: {{input}}' } }).then(r => r.json());
  const updated = await json(`/v1/prompts/${created.id}`, { method: 'PATCH', token, body: { title: 'Daily standup' } }).then(r => r.json());
  assert.equal(updated.title, 'Daily standup'); assert.equal(updated.content, created.content);
  assert.equal((await json('/v1/prompts', { method: 'POST', token, body: { title: '' } })).status, 400);
  assert.equal((await json(`/v1/prompts/${created.id}`, { method: 'DELETE', token })).status, 200);
  assert.equal((await json('/v1/prompts', { token }).then(r => r.json())).items.length, 0);
});

test('free plan: daily web search and image allowances, then a clear upgrade prompt', async () => {
  fake.on('https://api.search.brave.com/', () => Response.json({ web: { results: [] } }));
  fake.on('https://api.openai.com/v1/chat/completions', openaiOk('ok'));
  const { token, conversation } = await chatWith('openai', 'sk-test-1234567890');
  const ask = body => json(`/v1/conversations/${conversation.id}/messages`, { method: 'POST', token, body });
  for (let i = 0; i < 3; i++) assert.equal((await ask({ content: `search ${i}`, webSearch: true })).status, 201);
  const blocked = await ask({ content: 'one more', webSearch: true });
  assert.equal(blocked.status, 402);
  const error = (await blocked.json()).error;
  assert.equal(error.code, 'plan_limit'); assert.match(error.message, /free web searches.*BYAK Pro raises this to 200/);
  assert.equal((await ask({ content: 'no web is still fine' })).status, 201);

  const image = { type: 'image', mimeType: 'image/png', data: PNG };
  assert.equal((await ask({ content: 'pics', attachments: [image, image, image, image] })).status, 201);
  const tooMany = await ask({ content: 'pics', attachments: [image, image] });
  assert.equal(tooMany.status, 402); assert.match((await tooMany.json()).error.message, /1 left, this needs 2/);

  const plan = await json('/v1/subscription', { token }).then(r => r.json());
  assert.deepEqual(plan.usageToday, { webSearches: 3, images: 4 });
  assert.equal(plan.limits.webSearchesPerDay, 3); assert.ok(plan.highlights.some(h => h.key === 'webSearchesPerDay'));
});

test('free plan keeps a shorter conversation memory and fewer saved prompts', async () => {
  fake.on('https://api.openai.com/v1/chat/completions', openaiOk('ok'));
  const { token, conversation } = await chatWith('openai', 'sk-test-1234567890');
  for (let i = 0; i < 12; i++) await json(`/v1/conversations/${conversation.id}/messages`, { method: 'POST', token, body: { content: `message ${i}` } });
  const sent = fake.calls.at(-1).body.messages;
  assert.equal(sent.length, 1 + 20); // system prompt + last 20 messages on Free
  assert.equal(sent.at(-1).content, 'message 11');

  for (let i = 0; i < 5; i++) assert.equal((await json('/v1/prompts', { method: 'POST', token, body: { title: `p${i}`, content: 'x' } })).status, 201);
  const sixth = await json('/v1/prompts', { method: 'POST', token, body: { title: 'p6', content: 'x' } });
  assert.equal(sixth.status, 402); assert.match((await sixth.json()).error.message, /5 saved prompts/);
});
