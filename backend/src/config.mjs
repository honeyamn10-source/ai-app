import { randomBytes } from 'node:crypto';

const env = process.env;
const development = env.NODE_ENV !== 'production';
const masterKeyHex = env.BYAK_MASTER_KEY || (development ? '0'.repeat(64) : '');
const tokenSecret = env.BYAK_TOKEN_SECRET || (development ? 'development-only-token-secret-change-me' : '');
if (!/^[a-fA-F0-9]{64}$/.test(masterKeyHex)) throw new Error('BYAK_MASTER_KEY must be 64 hex characters');
if (tokenSecret.length < 32) throw new Error('BYAK_TOKEN_SECRET must be at least 32 characters');
if (!development && (/^0+$/.test(masterKeyHex) || tokenSecret.startsWith('development-only'))) throw new Error('Production requires real BYAK_MASTER_KEY and BYAK_TOKEN_SECRET values');

const flag = value => ['1', 'true', 'yes'].includes(String(value || '').toLowerCase());
const defaultGoogleWebClientId = '1077439001893-00b1peg0tcq60bcteadooohsbovdurfs.apps.googleusercontent.com';

export const config = Object.freeze({
  development,
  port: Number(env.PORT || 8787),
  origin: env.APP_ORIGIN || 'http://localhost:8787',
  masterKey: Buffer.from(masterKeyHex, 'hex'),
  tokenSecret,
  dataFile: env.BYAK_DATA_FILE || new URL('../data/store.json', import.meta.url).pathname,
  trustProxy: flag(env.TRUST_PROXY),
  allowLocalProviders: flag(env.ALLOW_LOCAL_PROVIDERS) || development,
  maxBodyBytes: 12 * 1024 * 1024,
  maxFileBytes: 8 * 1024 * 1024,
  accessTokenSeconds: 15 * 60,
  refreshTokenSeconds: 30 * 24 * 60 * 60,
  authRateLimit: Number(env.BYAK_AUTH_RATE_LIMIT || 20),
  apiRateLimit: Number(env.BYAK_API_RATE_LIMIT || 240),
  historyMessages: Number(env.BYAK_HISTORY_MESSAGES || 40),
  braveKey: env.BRAVE_SEARCH_API_KEY || '',
  githubToken: env.GITHUB_TOKEN || '',
  googleClientIds: [env.GOOGLE_ANDROID_CLIENT_ID, env.GOOGLE_WEB_CLIENT_ID || defaultGoogleWebClientId].filter(Boolean),
  play: Object.freeze({
    packageName: env.GOOGLE_PLAY_PACKAGE_NAME || 'ai.byak.app',
    serviceAccountJson: env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || '',
    // One subscription product with a base plan per billing period (matches Play Console).
    productId: env.PLAY_PRODUCT_ID || 'byak_pro',
    monthlyBasePlanId: env.PLAY_MONTHLY_BASE_PLAN_ID || 'monthly',
    yearlyBasePlanId: env.PLAY_YEARLY_BASE_PLAN_ID || 'yearly',
    rtdnToken: env.GOOGLE_PLAY_RTDN_TOKEN || ''
  }),
  requestId: () => randomBytes(8).toString('hex')
});
