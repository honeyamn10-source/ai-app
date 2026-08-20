import { assertSafeRemoteUrl, sanitizeText } from './security.mjs';
import { config } from './config.mjs';

export async function githubSearch(query) {
  const headers = { accept: 'application/vnd.github+json', 'user-agent': 'BYAK-AI/0.1' };
  if (config.githubToken) headers.authorization = `Bearer ${config.githubToken}`;
  const response = await fetch(`https://api.github.com/search/repositories?q=${encodeURIComponent(query)}&sort=stars&per_page=10`, { headers, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`GitHub search failed (${response.status})`);
  const data = await response.json();
  return data.items.map(x => ({ title: x.full_name, url: x.html_url, summary: sanitizeText(x.description, 500), stars: x.stargazers_count, license: x.license?.spdx_id || 'Unknown', updatedAt: x.updated_at }));
}

export async function redditSearch(query) {
  const response = await fetch(`https://www.reddit.com/search.json?q=${encodeURIComponent(query)}&sort=relevance&limit=10&raw_json=1`, { headers: { 'user-agent': 'BYAK-AI/0.1 (research client)' }, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`Reddit search failed (${response.status})`);
  const data = await response.json();
  return (data.data?.children || []).map(({ data: x }) => ({ title: x.title, url: `https://www.reddit.com${x.permalink}`, summary: sanitizeText(x.selftext, 700), subreddit: x.subreddit, score: x.score, createdAt: new Date(x.created_utc * 1000).toISOString() }));
}

export async function webSearch(query) {
  if (!config.braveKey) throw new Error('Web search is not configured; set BRAVE_SEARCH_API_KEY');
  const response = await fetch(`https://api.search.brave.com/res/v1/web/search?q=${encodeURIComponent(query)}&count=10`, { headers: { accept: 'application/json', 'x-subscription-token': config.braveKey }, signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`Web search failed (${response.status})`);
  const data = await response.json(); return (data.web?.results || []).map(x => ({ title: x.title, url: x.url, summary: x.description }));
}

export async function readUrl(input) {
  const url = assertSafeRemoteUrl(input);
  const response = await fetch(url, { redirect: 'error', headers: { 'user-agent': 'BYAK-AI/0.1' }, signal: AbortSignal.timeout(10000) });
  if (!response.ok) throw new Error(`URL retrieval failed (${response.status})`);
  const type = response.headers.get('content-type') || '';
  if (!type.includes('text/') && !type.includes('json')) throw new Error('Only text resources can be read');
  const text = sanitizeText(await response.text(), 100000);
  return { url: url.href, contentType: type, text: text.replace(/<script[\s\S]*?<\/script>/gi, '').replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim() };
}

