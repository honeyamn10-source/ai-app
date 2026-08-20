import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { randomUUID } from 'node:crypto';
import { config } from './config.mjs';

const empty = () => ({
  users: [], sessions: [], providers: [], conversations: [], messages: [], projects: [], files: [], memories: [], subscriptions: [], audit: []
});

export class Store {
  constructor(path = config.dataFile) { this.path = path; this.data = empty(); this.queue = Promise.resolve(); }
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
  remove(collection, predicate) { this.data[collection] = this.data[collection].filter(item => !predicate(item)); this.persist(); }
  audit(userId, action, metadata = {}) { this.insert('audit', { userId, action, metadata }); }
  async persist() {
    this.queue = this.queue.then(async () => {
      await mkdir(dirname(this.path), { recursive: true });
      const next = `${this.path}.next`;
      await writeFile(next, JSON.stringify(this.data, null, 2), { mode: 0o600 });
      await rename(next, this.path);
    });
    return this.queue;
  }
}

