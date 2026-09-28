import { createSign } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { config } from './config.mjs';
import { decryptSecret, encryptSecret, hashToken, safeEqual } from './security.mjs';

const failure = (status, message) => Object.assign(new Error(message), { status });

const freeLimits = { providers: 3, projects: 3, files: 25, memories: 50, researchPerDay: 50, webSearchesPerDay: 10, imagesPerDay: 12, savedPrompts: 5, historyMessages: 20, ragChunks: 4, comparisonsPerDay: 2 };
const proLimits = { providers: 50, projects: 200, files: 2000, memories: 2000, researchPerDay: 1000, webSearchesPerDay: 200, imagesPerDay: 500, savedPrompts: 200, historyMessages: 100, ragChunks: 10, comparisonsPerDay: 100 };
const proEntitlements = ['byok', 'basic_chat', 'basic_research', 'basic_exports', 'pro_chat', 'web_search', 'vision', 'model_compare', 'long_memory', 'deep_document_search', 'unlimited_projects', 'large_knowledge_base', 'priority_research'];

export const plans = Object.freeze({
  free: { id: 'free', tier: 'free', price: 0, currency: 'USD', entitlements: ['byok', 'basic_chat', 'basic_research', 'basic_exports'], limits: freeLimits },
  monthly: { id: 'monthly', tier: 'pro', price: 1, currency: 'USD', period: 'P1M', entitlements: proEntitlements, limits: proLimits },
  annual: { id: 'annual', tier: 'pro', price: 10, currency: 'USD', period: 'P1Y', entitlements: proEntitlements, limits: proLimits }
});

/** What Pro adds, for paywalls and the plans screen. */
export const proHighlights = Object.freeze([
  { key: 'webSearchesPerDay', title: 'Live web search in chat', free: '10 per day', pro: '200 per day' },
  { key: 'imagesPerDay', title: 'Ask about photos', free: '12 images per day', pro: '500 per day' },
  { key: 'comparisonsPerDay', title: 'Compare two models side by side', free: '2 per day', pro: '100 per day' },
  { key: 'historyMessages', title: 'Conversation memory', free: 'Last 20 messages', pro: 'Last 100 messages' },
  { key: 'ragChunks', title: 'Document search depth', free: '4 excerpts per answer', pro: '10 excerpts per answer' },
  { key: 'projects', title: 'Projects', free: '3', pro: '200' },
  { key: 'files', title: 'Knowledge files', free: '25', pro: '2,000' },
  { key: 'savedPrompts', title: 'Saved prompts', free: '5', pro: '200' },
  { key: 'researchPerDay', title: 'Research searches', free: '50 per day', pro: '1,000 per day' }
]);

const stateMap = {
  SUBSCRIPTION_STATE_ACTIVE: 'active', SUBSCRIPTION_STATE_IN_GRACE_PERIOD: 'grace', SUBSCRIPTION_STATE_ON_HOLD: 'on_hold',
  SUBSCRIPTION_STATE_PAUSED: 'paused', SUBSCRIPTION_STATE_CANCELED: 'cancelled', SUBSCRIPTION_STATE_EXPIRED: 'expired',
  SUBSCRIPTION_STATE_PENDING: 'pending', SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED: 'expired'
};
// A cancelled subscription keeps access until the end of the period the user already paid for.
const entitledStatuses = new Set(['active', 'grace', 'cancelled']);

function loadServiceAccount(raw) {
  if (!raw) return null;
  const text = raw.trim().startsWith('{') ? raw : readFileSync(raw, 'utf8');
  const account = JSON.parse(text);
  if (!account.client_email || !account.private_key) throw new Error('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is missing client_email or private_key');
  return account;
}

export function createBilling({ store, fetchImpl = (...args) => fetch(...args), now = () => Date.now(), play = config.play } = {}) {
  let account; let cachedToken = null;
  const serviceAccount = () => (account ??= loadServiceAccount(play.serviceAccountJson));
  const basePlans = { [play.monthlyBasePlanId]: 'monthly', [play.yearlyBasePlanId]: 'annual' };
  /** Maps a subscriptionsv2 line item to our plan via its product and base plan (e.g. byak_pro / yearly). */
  const planFor = item => (item?.productId === play.productId ? basePlans[item.offerDetails?.basePlanId] : undefined);
  const configured = () => Boolean(play.serviceAccountJson);
  const api = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(play.packageName)}`;

  /** Stable, non-reversible per-user id; the app passes it to Play as obfuscatedAccountId so a purchase token can't be redeemed by another account. */
  const accountIdFor = userId => hashToken(`play-account:${userId}`).slice(0, 64);

  async function accessToken() {
    if (cachedToken && cachedToken.expiresAt > now() + 60000) return cachedToken.value;
    const sa = serviceAccount(); const iat = Math.floor(now() / 1000);
    const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
    const claims = Buffer.from(JSON.stringify({ iss: sa.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher', aud: sa.token_uri || 'https://oauth2.googleapis.com/token', iat, exp: iat + 3600 })).toString('base64url');
    const signature = createSign('RSA-SHA256').update(`${header}.${claims}`).sign(sa.private_key, 'base64url');
    const response = await fetchImpl(sa.token_uri || 'https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${header}.${claims}.${signature}` }), signal: AbortSignal.timeout(10000) });
    if (!response.ok) throw failure(502, `Google OAuth token exchange failed (${response.status})`);
    const data = await response.json();
    cachedToken = { value: data.access_token, expiresAt: now() + (data.expires_in || 3600) * 1000 };
    return cachedToken.value;
  }

  async function fetchSubscription(purchaseToken) {
    const response = await fetchImpl(`${api}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`, { headers: { authorization: `Bearer ${await accessToken()}` }, signal: AbortSignal.timeout(15000) });
    if (response.status === 404 || response.status === 410 || response.status === 400) throw failure(400, 'Google Play does not recognise this purchase token');
    if (!response.ok) throw failure(502, `Google Play verification failed (${response.status})`);
    return response.json();
  }

  async function acknowledge(productId, purchaseToken) {
    const response = await fetchImpl(`${api}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`, { method: 'POST', headers: { authorization: `Bearer ${await accessToken()}`, 'content-type': 'application/json' }, body: '{}', signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw failure(502, `Google Play acknowledgement failed (${response.status})`);
  }

  /** Writes Google's view of a purchase onto our record. Google is the source of truth; notification payloads are never trusted directly. */
  async function sync(record, purchaseToken, data, source) {
    const lineItem = (data.lineItems || []).find(planFor);
    if (!lineItem) throw failure(400, 'This purchase is not a BYAK AI subscription');
    const status = stateMap[data.subscriptionState] || 'pending';
    const patch = {
      productId: lineItem.productId, plan: planFor(lineItem), basePlanId: lineItem.offerDetails.basePlanId, offerId: lineItem.offerDetails.offerId || null, status,
      expiresAt: lineItem.expiryTime || null, autoRenewing: Boolean(lineItem.autoRenewingPlan?.autoRenewEnabled),
      acknowledged: data.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED', testPurchase: Boolean(data.testPurchase),
      orderId: data.latestOrderId || null, verifiedAt: new Date(now()).toISOString()
    };
    if (data.linkedPurchaseToken) {
      const linkedHash = hashToken(data.linkedPurchaseToken);
      for (const old of store.filter('subscriptions', x => x.tokenHash === linkedHash && x.userId === record.userId && x.status !== 'replaced')) store.update('subscriptions', old.id, { status: 'replaced' });
    }
    const saved = store.update('subscriptions', record.id, patch);
    if (!saved.acknowledged && (status === 'active' || status === 'grace')) {
      try { await acknowledge(lineItem.productId, purchaseToken); store.update('subscriptions', record.id, { acknowledged: true }); }
      catch (error) { console.error(JSON.stringify({ level: 'error', message: 'play acknowledgement failed; will retry on next verification', subscriptionId: record.id, error: error.message })); }
    }
    store.insert('subscriptionEvents', { subscriptionId: record.id, userId: record.userId, source, status, productId: lineItem.productId, expiresAt: patch.expiresAt });
    return saved;
  }

  async function verify(userId, purchaseToken) {
    if (!configured()) throw failure(503, 'Purchases can’t be verified yet: the server is missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON');
    const tokenHash = hashToken(purchaseToken);
    let record = store.find('subscriptions', x => x.tokenHash === tokenHash);
    if (record && record.userId !== userId) throw failure(409, 'This purchase is already linked to a different BYAK account');
    const data = await fetchSubscription(purchaseToken);
    const owner = data.externalAccountIdentifiers?.obfuscatedExternalAccountId;
    if (owner && owner !== accountIdFor(userId)) throw failure(403, 'This purchase was made for a different BYAK account');
    record ??= store.insert('subscriptions', { userId, platform: 'google_play', tokenHash, tokenEncrypted: encryptSecret(purchaseToken, `play-token:${userId}`), status: 'pending' });
    record = await sync(record, purchaseToken, data, 'client_verify');
    store.audit(userId, 'billing.verified', { productId: record.productId, status: record.status });
    return entitlement(userId);
  }

  /** Google Play Real-time developer notification delivered by a Pub/Sub push subscription. */
  async function handleNotification(body, token) {
    if (!play.rtdnToken) throw failure(503, 'Notifications are not configured');
    if (!token || !safeEqual(token, play.rtdnToken)) throw failure(401, 'Invalid notification token');
    let payload;
    try { payload = JSON.parse(Buffer.from(body?.message?.data || '', 'base64').toString('utf8')); } catch { return { ignored: 'unparseable' }; }
    if (payload.packageName && payload.packageName !== play.packageName) return { ignored: 'package' };
    const purchaseToken = payload.subscriptionNotification?.purchaseToken || payload.voidedPurchaseNotification?.purchaseToken;
    if (!purchaseToken) return { ignored: payload.testNotification ? 'test' : 'type' };
    const record = store.find('subscriptions', x => x.tokenHash === hashToken(purchaseToken));
    if (!record) return { ignored: 'unknown_token' }; // the app links it on its next verify call
    if (payload.voidedPurchaseNotification) {
      store.update('subscriptions', record.id, { status: 'revoked', autoRenewing: false });
      store.insert('subscriptionEvents', { subscriptionId: record.id, userId: record.userId, source: 'rtdn_voided', status: 'revoked' });
      return { updated: record.id, status: 'revoked' };
    }
    const saved = await sync(record, purchaseToken, await fetchSubscription(purchaseToken), `rtdn_${payload.subscriptionNotification?.notificationType ?? 'unknown'}`);
    return { updated: saved.id, status: saved.status };
  }

  const isEntitled = record => entitledStatuses.has(record.status) && record.expiresAt && Date.parse(record.expiresAt) > now();

  /** The user's effective plan. Re-checks with Google when a renewing subscription has passed its expiry (renewal may have happened). */
  async function refresh(userId) {
    if (!configured()) return;
    const stale = store.filter('subscriptions', x => x.userId === userId && x.autoRenewing && entitledStatuses.has(x.status) && x.expiresAt && Date.parse(x.expiresAt) <= now() && (!x.verifiedAt || now() - Date.parse(x.verifiedAt) > 10 * 60 * 1000));
    for (const record of stale) {
      try { const token = decryptSecret(record.tokenEncrypted, `play-token:${userId}`); await sync(record, token, await fetchSubscription(token), 'lazy_refresh'); }
      catch (error) { console.error(JSON.stringify({ level: 'warn', message: 'subscription refresh failed', subscriptionId: record.id, error: error.message })); }
    }
  }

  function entitlement(userId) {
    const active = store.filter('subscriptions', x => x.userId === userId && isEntitled(x)).sort((a, b) => Date.parse(b.expiresAt) - Date.parse(a.expiresAt))[0];
    const plan = plans[active?.plan] || plans.free;
    return {
      plan: plan.id, tier: plan.tier, status: active ? active.status : 'active', entitlements: plan.entitlements, limits: plan.limits,
      productId: active?.productId || null, basePlanId: active?.basePlanId || null, expiresAt: active?.expiresAt || null, autoRenewing: active?.autoRenewing ?? false,
      platform: active?.platform || null, billingAccountId: accountIdFor(userId), verificationAvailable: configured()
    };
  }

  const productIds = () => ({ productId: play.productId, monthly: play.monthlyBasePlanId, annual: play.yearlyBasePlanId });
  return { verify, handleNotification, entitlement, refresh, accountIdFor, configured, productIds };
}
