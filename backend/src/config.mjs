import { randomBytes } from 'node:crypto';

const development = process.env.NODE_ENV !== 'production';
const masterKeyHex = process.env.BYAK_MASTER_KEY || (development ? '0'.repeat(64) : '');
const tokenSecret = process.env.BYAK_TOKEN_SECRET || (development ? 'development-only-token-secret-change-me' : '');
const googlePlayServiceAccount = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
  ? JSON.parse(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON)
  : null;
if (!/^[a-fA-F0-9]{64}$/.test(masterKeyHex)) throw new Error('BYAK_MASTER_KEY must be 64 hex characters');
if (tokenSecret.length < 32) throw new Error('BYAK_TOKEN_SECRET must be at least 32 characters');

export const config = Object.freeze({
  development,
  port: Number(process.env.PORT || 8787),
  origin: process.env.APP_ORIGIN || 'http://localhost:8787',
  masterKey: Buffer.from(masterKeyHex, 'hex'),
  tokenSecret,
  dataFile: process.env.BYAK_DATA_FILE || new URL('../data/store.json', import.meta.url).pathname,
  maxBodyBytes: 10 * 1024 * 1024,
  maxFileBytes: 8 * 1024 * 1024,
  accessTokenSeconds: 15 * 60,
  refreshTokenSeconds: 30 * 24 * 60 * 60,
  braveKey: process.env.BRAVE_SEARCH_API_KEY || '',
  githubToken: process.env.GITHUB_TOKEN || '',
  googleClientIds: [process.env.GOOGLE_ANDROID_CLIENT_ID, process.env.GOOGLE_WEB_CLIENT_ID].filter(Boolean),
  googlePlayServiceAccount,
  googlePlayPackageName: process.env.GOOGLE_PLAY_PACKAGE_NAME || 'ai.byak.app',
  googlePlayProducts: new Set(
    (process.env.GOOGLE_PLAY_PRODUCT_IDS || 'byak_pro')
      .split(',')
      .map(value => value.trim())
      .filter(Boolean),
  ),
  requestId: () => randomBytes(8).toString('hex')
});
