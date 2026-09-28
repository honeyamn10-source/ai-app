import { assertSafeRemoteUrl, sanitizeText } from './security.mjs';
import { config } from './config.mjs';

const failure = (status, message) => Object.assign(new Error(message), { status });
const upstream = (label, response) => failure(response.status === 429 ? 429 : 502, `${label} failed (${response.status})`);

export async function githubSearch(query) {
  const headers = { accept: 'application/vnd.github+json', 'user-agent': 'BYAK-AI/0.2' };
  if (config.githubToken) headers.authorization = `Bearer ${config.githubToken}`;
  const response = await fetch(`https://api.github.com/search/repositories?q=${encodeURIComponent(query)}&sort=stars&per_page=10`, { headers, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw upstream('GitHub search', response);
  const data = await response.json();
  return (data.items || []).map(x => ({ title: x.full_name, url: x.html_url, summary: sanitizeText(x.description, 500), stars: x.stargazers_count, license: x.license?.spdx_id || 'Unknown', updatedAt: x.updated_at }));
}

export async function redditSearch(query) {
  const response = await fetch(`https://www.reddit.com/search.json?q=${encodeURIComponent(query)}&sort=relevance&limit=10&raw_json=1`, { headers: { 'user-agent': 'BYAK-AI/0.2 (research client)' }, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw upstream('Reddit search', response);
  const data = await response.json();
  return (data.data?.children || []).map(({ data: x }) => ({ title: x.title, url: `https://www.reddit.com${x.permalink}`, summary: sanitizeText(x.selftext, 700), subreddit: x.subreddit, score: x.score, createdAt: new Date(x.created_utc * 1000).toISOString() }));
}

export async function webSearch(query) {
  if (!config.braveKey) throw failure(503, 'Web search is not configured on this server; try GitHub or Reddit, or set BRAVE_SEARCH_API_KEY');
  const response = await fetch(`https://api.search.brave.com/res/v1/web/search?q=${encodeURIComponent(query)}&count=10`, { headers: { accept: 'application/json', 'x-subscription-token': config.braveKey }, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw upstream('Web search', response);
  const data = await response.json(); return (data.web?.results || []).map(x => ({ title: x.title, url: x.url, summary: sanitizeText(x.description, 700) }));
}

export function htmlToText(html) {
  const title = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]?.trim() || '';
  const text = html.replace(/<(script|style|noscript|svg|template)[\s\S]*?<\/\1>/gi, ' ').replace(/<!--[\s\S]*?-->/g, ' ').replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/\s+/g, ' ').trim();
  return { title, text };
}

export async function readUrl(input, { maxBytes = 2 * 1024 * 1024 } = {}) {
  const url = await assertSafeRemoteUrl(input);
  const response = await fetch(url, { redirect: 'error', headers: { 'user-agent': 'BYAK-AI/0.2', accept: 'text/html,text/plain,application/json;q=0.9' }, signal: AbortSignal.timeout(10000) }).catch(error => { throw failure(502, `URL retrieval failed: ${error.cause?.message || error.message}`); });
  if (!response.ok) throw upstream('URL retrieval', response);
  const type = response.headers.get('content-type') || '';
  if (!type.includes('text/') && !type.includes('json')) throw failure(415, 'Only text resources can be read');
  const parts = []; let size = 0;
  for await (const chunk of response.body) { size += chunk.length; if (size > maxBytes) break; parts.push(chunk); }
  const raw = sanitizeText(Buffer.concat(parts).toString('utf8'), maxBytes);
  const { title, text } = type.includes('html') ? htmlToText(raw) : { title: '', text: raw.trim() };
  return { url: url.href, contentType: type, title, text: text.slice(0, 100000), truncated: size > maxBytes || text.length > 100000 };
}
