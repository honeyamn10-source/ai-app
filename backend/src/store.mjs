import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { randomUUID } from 'node:crypto';
import { config } from './config.mjs';

const collections = ['users', 'sessions', 'providers', 'conversations', 'messages', 'projects', 'files', 'memories', 'prompts', 'subscriptions', 'subscriptionEvents', 'usage', 'audit'];
const empty = () => Object.fromEntries(collections.map(name => [name, []]));

export class Store {
  constructor(path = config.dataFile) { this.path = path; this.data = empty(); this.writing = null; this.dirty = false; }
  async init() {
    try { this.data = { ...empty(), ...JSON.parse(await readFile(this.path, 'utf8')) }; }
    catch (error) { if (error.code !== 'ENOENT') throw error; await this.persist(); }
    return this;
  }
  id() { return randomUUID(); }
  find(collection, predicate) { return this.data[collection].find(predicate); }
  filter(collection, predicate) { return this.data[collection].filter(predicate); }
  insert(collection, value) {
    const now = new Date().toISOString();
    const row = { id: this.id(), createdAt: now, updatedAt: now, ...value };
    this.data[collection].push(row); this.persist(); return row;
  }
  update(collection, id, patch) {
    const row = this.find(collection, item => item.id === id);
    if (!row) return null;
    Object.assign(row, patch, { updatedAt: new Date().toISOString() }); this.persist(); return row;
  }
  remove(collection, predicate) { const before = this.data[collection].length; this.data[collection] = this.data[collection].filter(item => !predicate(item)); this.persist(); return before - this.data[collection].length; }
  audit(userId, action, metadata = {}) { this.insert('audit', { userId, action, metadata }); }

  /** Coalesces bursts of changes into one atomic write; a failed write is logged and retried on the next change instead of poisoning later writes. */
  persist() {
    this.dirty = true;
    if (!this.writing) this.writing = this.#drain().finally(() => { this.writing = null; });
    return this.writing;
  }
  async #drain() {
    while (this.dirty) {
      this.dirty = false;
      try {
        await mkdir(dirname(this.path), { recursive: true });
        const next = `${this.path}.next`;
        await writeFile(next, JSON.stringify(this.data), { mode: 0o600 });
        await rename(next, this.path);
      } catch (error) {
        console.error(JSON.stringify({ level: 'error', message: 'store persist failed', error: error.message }));
        return;
      }
    }
  }
  async flush() { while (this.writing) await this.writing; }
}
