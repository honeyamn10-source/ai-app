import test from 'node:test';
import assert from 'node:assert/strict';
import { buildRagContext, chunkText, retrieve } from '../src/rag.mjs';

test('chunking creates overlapping bounded chunks', () => { const chunks = chunkText('alpha '.repeat(500), 200, 20); assert.ok(chunks.length > 5); assert.ok(chunks.every(x => x.text.length <= 200)); });
test('retrieval ranks matching content', () => { const result = retrieve('security encryption', [{ index: 0, text: 'gardening notes' }, { index: 1, text: 'security uses strong encryption' }]); assert.equal(result[0].index, 1); assert.match(buildRagContext(result), /Document source 1/); });

