import test from 'node:test';
import assert from 'node:assert/strict';
import { buildRagContext, chunkText, retrieve } from '../src/rag.mjs';
import { htmlToText } from '../src/research.mjs';

test('chunking creates overlapping bounded chunks', () => { const chunks = chunkText('alpha '.repeat(500), 200, 20); assert.ok(chunks.length > 5); assert.ok(chunks.every(x => x.text.length <= 200)); });
test('chunking prefers sentence boundaries', () => { const chunks = chunkText('One sentence here. '.repeat(40), 200, 20); assert.ok(chunks.slice(0, -1).every(x => x.text.endsWith('.'))); });
test('retrieval ranks matching content', () => { const result = retrieve('security encryption', [{ index: 0, text: 'gardening notes' }, { index: 1, text: 'security uses strong encryption' }]); assert.equal(result[0].index, 1); assert.match(buildRagContext(result), /Document source 1/); });
test('retrieval favours rare terms and supports non-English text', () => {
  const chunks = [{ index: 0, text: 'the project plan the project plan' }, { index: 1, text: 'the project uses kubernetes' }, { index: 2, text: 'le projet utilise kubernetes et sécurité' }];
  assert.equal(retrieve('project kubernetes', chunks)[0].index, 1);
  assert.equal(retrieve('sécurité', chunks)[0].index, 2);
  assert.deepEqual(retrieve('', chunks), []);
});
test('HTML extraction drops scripts and decodes entities', () => {
  const { title, text } = htmlToText('<html><title>Hi</title><script>evil()</script><p>Fish &amp; chips</p></html>');
  assert.equal(title, 'Hi'); assert.equal(text, 'Hi Fish & chips');
});
