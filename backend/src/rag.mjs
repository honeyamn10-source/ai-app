import { sanitizeText } from './security.mjs';

export function chunkText(text, size = 1200, overlap = 150) {
  const clean = sanitizeText(text, 500000).replace(/\r/g, '');
  const chunks = [];
  for (let start = 0; start < clean.length; start += size - overlap) {
    const value = clean.slice(start, start + size).trim(); if (value) chunks.push({ index: chunks.length, text: value });
  }
  return chunks;
}

const terms = text => new Set(String(text).toLowerCase().match(/[a-z0-9]{3,}/g) || []);
export function retrieve(query, chunks, limit = 6) {
  const wanted = terms(query);
  return chunks.map(chunk => {
    const present = terms(chunk.text); let score = 0;
    for (const term of wanted) if (present.has(term)) score += 1;
    return { ...chunk, score: wanted.size ? score / wanted.size : 0 };
  }).filter(x => x.score > 0).sort((a, b) => b.score - a.score).slice(0, limit);
}

export function buildRagContext(results) {
  return results.map((x, i) => `[Document source ${i + 1}, chunk ${x.index}]\n${x.text}`).join('\n\n');
}

