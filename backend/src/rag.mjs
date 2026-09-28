import { sanitizeText } from './security.mjs';

/** Splits text into overlapping chunks, preferring paragraph/sentence boundaries near the chunk end. */
export function chunkText(text, size = 1200, overlap = 150) {
  const clean = sanitizeText(text, 2000000).replace(/\r/g, '');
  const chunks = [];
  let start = 0;
  while (start < clean.length) {
    let end = Math.min(start + size, clean.length);
    if (end < clean.length) {
      const window = clean.slice(start + Math.floor(size * 0.6), end);
      const cut = Math.max(window.lastIndexOf('\n\n'), window.lastIndexOf('. '), window.lastIndexOf('\n'));
      if (cut > 0) end = start + Math.floor(size * 0.6) + cut + 1;
    }
    const value = clean.slice(start, end).trim();
    if (value) chunks.push({ index: chunks.length, text: value });
    if (end >= clean.length) break;
    start = Math.max(end - overlap, start + 1);
  }
  return chunks;
}

const stopWords = new Set(['the', 'and', 'for', 'are', 'but', 'not', 'you', 'all', 'can', 'her', 'was', 'one', 'our', 'out', 'has', 'have', 'this', 'that', 'with', 'what', 'from', 'they', 'will', 'would', 'there', 'their', 'about', 'which', 'when', 'your', 'how', 'does', 'into', 'than', 'then', 'them', 'these', 'some']);
export const tokenize = text => (String(text).toLowerCase().match(/[\p{L}\p{N}]{2,}/gu) || []).filter(t => !stopWords.has(t));

/** BM25 ranking over the candidate chunks. Scores are normalised to 0..1 for the best match. */
export function retrieve(query, chunks, limit = 6) {
  const wanted = [...new Set(tokenize(query))];
  if (!wanted.length || !chunks.length) return [];
  const docs = chunks.map(chunk => { const tokens = tokenize(chunk.text); const tf = new Map(); for (const t of tokens) tf.set(t, (tf.get(t) || 0) + 1); return { chunk, tf, length: tokens.length }; });
  const avg = docs.reduce((sum, d) => sum + d.length, 0) / docs.length || 1;
  const df = new Map(wanted.map(t => [t, docs.filter(d => d.tf.has(t)).length]));
  const k1 = 1.4, b = 0.75;
  const scored = docs.map(d => {
    let score = 0;
    for (const t of wanted) {
      const f = d.tf.get(t); if (!f) continue;
      const idf = Math.log(1 + (docs.length - df.get(t) + 0.5) / (df.get(t) + 0.5));
      score += idf * (f * (k1 + 1)) / (f + k1 * (1 - b + b * d.length / avg));
    }
    return { ...d.chunk, score };
  }).filter(x => x.score > 0).sort((a, b2) => b2.score - a.score).slice(0, limit);
  const top = scored[0]?.score || 1;
  return scored.map(x => ({ ...x, score: Number((x.score / top).toFixed(4)) }));
}

export function buildRagContext(results) {
  return results.map((x, i) => `[Document source ${i + 1}: ${x.fileName || 'document'}, chunk ${x.index}]\n${x.text}`).join('\n\n');
}
