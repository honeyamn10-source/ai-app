import { config } from './config.mjs';
import { assertSafeRemoteUrl, decryptSecret } from './security.mjs';

export const providerCatalog = {
  openai: { name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', kind: 'openai', usageOption: true, models: ['gpt-4.1-mini', 'gpt-4.1', 'o4-mini'] },
  anthropic: { name: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1', kind: 'anthropic', models: ['claude-opus-5', 'claude-sonnet-5', 'claude-haiku-4-5'] },
  gemini: { name: 'Google Gemini', baseUrl: 'https://generativelanguage.googleapis.com/v1beta', kind: 'gemini', models: ['gemini-2.5-flash', 'gemini-2.5-pro'] },
  openrouter: { name: 'OpenRouter', baseUrl: 'https://openrouter.ai/api/v1', kind: 'openai', usageOption: true, models: ['openai/gpt-4.1-mini', 'google/gemini-2.5-flash', 'anthropic/claude-sonnet-5'] },
  groq: { name: 'Groq', baseUrl: 'https://api.groq.com/openai/v1', kind: 'openai', usageOption: true, models: ['llama-3.3-70b-versatile'] },
  mistral: { name: 'Mistral', baseUrl: 'https://api.mistral.ai/v1', kind: 'openai', models: ['mistral-small-latest', 'mistral-large-latest'] },
  deepseek: { name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', kind: 'openai', usageOption: true, models: ['deepseek-chat', 'deepseek-reasoner'] },
  ollama: { name: 'Ollama', baseUrl: 'http://127.0.0.1:11434/v1', kind: 'openai', models: [], localOnly: true, keyOptional: true }
};

const failure = (status, message) => Object.assign(new Error(message), { status });

function credential(connection) { return connection.secret ? decryptSecret(connection.secret, `provider:${connection.userId}:${connection.id}`) : ''; }

function headers(connection, catalog) {
  const key = credential(connection);
  if (catalog.kind === 'anthropic') return { 'x-api-key': key, 'anthropic-version': '2023-06-01', 'content-type': 'application/json' };
  if (catalog.kind === 'gemini') return { 'x-goog-api-key': key, 'content-type': 'application/json' };
  const result = { 'content-type': 'application/json' };
  if (key) result.authorization = `Bearer ${key}`;
  if (connection.provider === 'openrouter') { result['http-referer'] = config.origin; result['x-title'] = 'BYAK AI'; }
  return result;
}

/** Only custom endpoints (and local Ollama when explicitly allowed) may override the base URL. */
async function endpoint(connection, path) {
  const catalog = providerCatalog[connection.provider] || { baseUrl: connection.baseUrl, kind: 'openai' };
  let base = catalog.baseUrl;
  if (connection.provider === 'custom') base = (await assertSafeRemoteUrl(connection.baseUrl)).href;
  else if (connection.provider === 'ollama') {
    if (!config.allowLocalProviders) throw failure(400, 'Local providers are disabled on this server');
    base = connection.baseUrl || catalog.baseUrl;
  }
  return { catalog, url: `${base.replace(/\/+$/, '')}${path}` };
}

async function providerError(response, label) {
  let detail = '';
  try { const text = await response.text(); const data = JSON.parse(text); detail = data.error?.message || data.message || data[0]?.error?.message || ''; } catch { /* body was not JSON */ }
  const status = response.status === 401 || response.status === 403 ? 400 : response.status === 429 ? 429 : 502;
  const hint = response.status === 401 || response.status === 403 ? 'the provider rejected your API key' : response.status === 429 ? 'rate limited or out of credit at the provider' : `error ${response.status}`;
  return failure(status, `${label}: ${hint}${detail ? ` — ${String(detail).slice(0, 300)}` : ''}`);
}

export async function validateProvider(connection) {
  const { catalog, url } = await endpoint(connection, '/models');
  const response = await fetch(url, { headers: headers(connection, catalog), signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw await providerError(response, catalog.name || 'Provider');
  return true;
}

export async function listRemoteModels(connection) {
  const kind = providerCatalog[connection.provider]?.kind || 'openai';
  const { catalog, url } = await endpoint(connection, kind === 'gemini' ? '/models?pageSize=200' : '/models');
  const response = await fetch(url, { headers: headers(connection, catalog), signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw await providerError(response, catalog.name || 'Provider');
  const data = await response.json();
  if (catalog.kind === 'gemini') return (data.models || []).filter(x => (x.supportedGenerationMethods || []).includes('generateContent')).map(x => String(x.name).replace(/^models\//, '')).slice(0, 200);
  return (data.data || data.models || []).map(x => x.id || x.name).filter(Boolean).sort().slice(0, 300);
}

/** Splits a fetch body into SSE `data:` payloads. */
async function* sseData(body) {
  const decoder = new TextDecoder(); let buffer = '';
  for await (const chunk of body) {
    buffer += decoder.decode(chunk, { stream: true });
    let index;
    while ((index = buffer.search(/\r?\n/)) >= 0) {
      const line = buffer.slice(0, index); buffer = buffer.slice(index + (buffer[index] === '\r' ? 2 : 1));
      if (line.startsWith('data:')) yield line.slice(5).trim();
    }
  }
  if (buffer.startsWith('data:')) yield buffer.slice(5).trim();
}

const parse = value => { try { return JSON.parse(value); } catch { return null; } };

/** Merges consecutive same-role turns (some APIs require alternation) and keeps any attached images. */
function mergeTurns(messages) {
  const result = [];
  for (const m of messages) {
    const images = m.images || [];
    if (!m.content && !images.length) continue;
    const last = result.at(-1);
    if (last && last.role === m.role) { last.content = [last.content, m.content].filter(Boolean).join('\n\n'); last.images.push(...images); }
    else result.push({ role: m.role, content: m.content || '', images: [...images] });
  }
  return result;
}

// Provider-specific shapes for a turn that may carry images ({ mimeType, data(base64) }).
const anthropicTurn = m => ({ role: m.role, content: m.images.length ? [...m.images.map(i => ({ type: 'image', source: { type: 'base64', media_type: i.mimeType, data: i.data } })), { type: 'text', text: m.content || 'Describe this image.' }] : m.content });
const geminiTurn = m => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [...m.images.map(i => ({ inline_data: { mime_type: i.mimeType, data: i.data } })), { text: m.content || 'Describe this image.' }] });
const openaiTurn = m => ({ role: m.role, content: m.images.length ? [{ type: 'text', text: m.content || 'Describe this image.' }, ...m.images.map(i => ({ type: 'image_url', image_url: { url: `data:${i.mimeType};base64,${i.data}` } }))] : m.content });

/**
 * Streams a chat completion. Calls onDelta(text) for each chunk and resolves to { text, usage, stopReason }.
 * `temperature` is only forwarded when explicitly set: current Claude models and OpenAI reasoning models reject it.
 */
export async function streamChat(connection, { model, messages, temperature, signal, onDelta = () => {} }) {
  if (!model) throw failure(400, 'Choose a model for this provider');
  const { catalog } = await endpoint(connection, '');
  const timeout = AbortSignal.timeout(5 * 60 * 1000);
  const abort = signal ? AbortSignal.any([signal, timeout]) : timeout;
  const system = messages.filter(m => m.role === 'system').map(m => m.content).join('\n\n');
  const turns = mergeTurns(messages.filter(m => m.role !== 'system'));
  let text = ''; const usage = { inputTokens: 0, outputTokens: 0 }; let stopReason = null;
  const emit = delta => { if (delta) { text += delta; onDelta(delta); } };

  if (catalog.kind === 'anthropic') {
    const { url } = await endpoint(connection, '/messages');
    const body = { model, max_tokens: 16000, stream: true, messages: turns.map(anthropicTurn) };
    if (system) body.system = system;
    if (typeof temperature === 'number') body.temperature = temperature;
    const response = await fetch(url, { method: 'POST', headers: headers(connection, catalog), body: JSON.stringify(body), signal: abort });
    if (!response.ok) throw await providerError(response, 'Anthropic');
    for await (const raw of sseData(response.body)) {
      const event = parse(raw); if (!event) continue;
      if (event.type === 'message_start') usage.inputTokens = event.message?.usage?.input_tokens || 0;
      else if (event.type === 'content_block_delta' && event.delta?.type === 'text_delta') emit(event.delta.text);
      else if (event.type === 'message_delta') { usage.outputTokens = event.usage?.output_tokens || usage.outputTokens; stopReason = event.delta?.stop_reason || stopReason; }
      else if (event.type === 'error') throw failure(502, `Anthropic: ${event.error?.message || 'stream error'}`);
    }
    if (stopReason === 'refusal' && !text) emit('The model declined to answer this request.');
  } else if (catalog.kind === 'gemini') {
    const { url } = await endpoint(connection, `/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse`);
    const body = { contents: turns.map(geminiTurn) };
    if (system) body.systemInstruction = { parts: [{ text: system }] };
    if (typeof temperature === 'number') body.generationConfig = { temperature };
    const response = await fetch(url, { method: 'POST', headers: headers(connection, catalog), body: JSON.stringify(body), signal: abort });
    if (!response.ok) throw await providerError(response, 'Gemini');
    for await (const raw of sseData(response.body)) {
      const event = parse(raw); if (!event) continue;
      if (event.error) throw failure(502, `Gemini: ${event.error.message || 'stream error'}`);
      const candidate = event.candidates?.[0];
      emit((candidate?.content?.parts || []).filter(p => !p.thought).map(p => p.text || '').join(''));
      if (candidate?.finishReason) stopReason = candidate.finishReason;
      if (event.usageMetadata) { usage.inputTokens = event.usageMetadata.promptTokenCount || 0; usage.outputTokens = event.usageMetadata.candidatesTokenCount || 0; }
    }
  } else {
    const { url } = await endpoint(connection, '/chat/completions');
    const body = { model, stream: true, messages: [...(system ? [{ role: 'system', content: system }] : []), ...turns.map(openaiTurn)] };
    if (catalog.usageOption) body.stream_options = { include_usage: true };
    if (typeof temperature === 'number') body.temperature = temperature;
    const response = await fetch(url, { method: 'POST', headers: headers(connection, catalog), body: JSON.stringify(body), signal: abort });
    if (!response.ok) throw await providerError(response, catalog.name || 'Provider');
    for await (const raw of sseData(response.body)) {
      if (raw === '[DONE]') break;
      const event = parse(raw); if (!event) continue;
      if (event.error) throw failure(502, `${catalog.name || 'Provider'}: ${event.error.message || 'stream error'}`);
      const choice = event.choices?.[0];
      emit(choice?.delta?.content || '');
      if (choice?.finish_reason) stopReason = choice.finish_reason;
      if (event.usage) { usage.inputTokens = event.usage.prompt_tokens || 0; usage.outputTokens = event.usage.completion_tokens || 0; }
    }
  }
  return { text, usage, stopReason };
}

export async function completeChat(connection, options) { return (await streamChat(connection, options)).text; }
