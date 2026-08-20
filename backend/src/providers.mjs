import { decryptSecret } from './security.mjs';

export const providerCatalog = {
  openai: { name: 'OpenAI', baseUrl: 'https://api.openai.com/v1', kind: 'openai', models: ['gpt-4.1-mini', 'gpt-4.1', 'o4-mini'] },
  anthropic: { name: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1', kind: 'anthropic', models: ['claude-sonnet-4-20250514', 'claude-3-5-haiku-latest'] },
  gemini: { name: 'Google Gemini', baseUrl: 'https://generativelanguage.googleapis.com/v1beta', kind: 'gemini', models: ['gemini-2.5-flash', 'gemini-2.5-pro'] },
  openrouter: { name: 'OpenRouter', baseUrl: 'https://openrouter.ai/api/v1', kind: 'openai', models: ['openai/gpt-4.1-mini', 'google/gemini-2.5-flash'] },
  groq: { name: 'Groq', baseUrl: 'https://api.groq.com/openai/v1', kind: 'openai', models: ['llama-3.3-70b-versatile'] },
  mistral: { name: 'Mistral', baseUrl: 'https://api.mistral.ai/v1', kind: 'openai', models: ['mistral-small-latest', 'mistral-large-latest'] },
  deepseek: { name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', kind: 'openai', models: ['deepseek-chat', 'deepseek-reasoner'] },
  ollama: { name: 'Ollama', baseUrl: 'http://127.0.0.1:11434/v1', kind: 'openai', models: [], localOnly: true }
};

function credential(connection) { return decryptSecret(connection.secret, `provider:${connection.userId}:${connection.id}`); }
function headers(connection, catalog) {
  const key = credential(connection);
  if (catalog.kind === 'anthropic') return { 'x-api-key': key, 'anthropic-version': '2023-06-01', 'content-type': 'application/json' };
  return { authorization: `Bearer ${key}`, 'content-type': 'application/json' };
}

function endpoint(connection, path) {
  const catalog = providerCatalog[connection.provider] || { baseUrl: connection.baseUrl, kind: 'openai' };
  const base = (connection.baseUrl || catalog.baseUrl).replace(/\/$/, '');
  return { catalog, url: `${base}${path}` };
}

export async function validateProvider(connection) {
  if (connection.provider === 'gemini') {
    const key = credential(connection); const { url } = endpoint(connection, `/models?key=${encodeURIComponent(key)}`);
    const response = await fetch(url, { signal: AbortSignal.timeout(10000) });
    if (!response.ok) throw new Error(`Provider rejected credentials (${response.status})`);
    return true;
  }
  const { catalog, url } = endpoint(connection, '/models');
  const response = await fetch(url, { headers: headers(connection, catalog), signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error(`Provider rejected credentials (${response.status})`);
  return true;
}

export async function completeChat(connection, { model, messages, temperature = 0.3 }) {
  const { catalog } = endpoint(connection, '');
  if (catalog.kind === 'gemini') {
    const key = credential(connection);
    const url = `${(connection.baseUrl || catalog.baseUrl).replace(/\/$/, '')}/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(key)}`;
    const body = { contents: messages.filter(m => m.role !== 'system').map(m => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] })), generationConfig: { temperature } };
    const response = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body), signal: AbortSignal.timeout(90000) });
    if (!response.ok) throw new Error(`Gemini error ${response.status}`);
    const data = await response.json(); return data.candidates?.[0]?.content?.parts?.map(p => p.text || '').join('') || '';
  }
  if (catalog.kind === 'anthropic') {
    const { url } = endpoint(connection, '/messages');
    const system = messages.filter(m => m.role === 'system').map(m => m.content).join('\n');
    const body = { model, max_tokens: 4096, temperature, system, messages: messages.filter(m => m.role !== 'system') };
    const response = await fetch(url, { method: 'POST', headers: headers(connection, catalog), body: JSON.stringify(body), signal: AbortSignal.timeout(90000) });
    if (!response.ok) throw new Error(`Anthropic error ${response.status}`);
    const data = await response.json(); return data.content?.map(x => x.text || '').join('') || '';
  }
  const { url } = endpoint(connection, '/chat/completions');
  const response = await fetch(url, { method: 'POST', headers: headers(connection, catalog), body: JSON.stringify({ model, messages, temperature }), signal: AbortSignal.timeout(90000) });
  if (!response.ok) throw new Error(`Provider error ${response.status}`);
  const data = await response.json(); return data.choices?.[0]?.message?.content || '';
}

export async function listRemoteModels(connection) {
  if (connection.provider === 'gemini') return providerCatalog.gemini.models;
  const { catalog, url } = endpoint(connection, '/models');
  const response = await fetch(url, { headers: headers(connection, catalog), signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`Provider error ${response.status}`);
  const data = await response.json();
  return (data.data || data.models || []).map(x => x.id || x.name).filter(Boolean).slice(0, 200);
}

