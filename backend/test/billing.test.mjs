import test from 'node:test';
import assert from 'node:assert/strict';
import { createVerify, generateKeyPairSync } from 'node:crypto';
import { isolateStore } from './helpers.mjs';

isolateStore();
const { Store } = await import('../src/store.mjs');
const { createBilling } = await import('../src/billing.mjs');

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const serviceAccount = JSON.stringify({ client_email: 'play@byak.iam.gserviceaccount.com', private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }), token_uri: 'https://oauth2.googleapis.com/token' });
const play = { packageName: 'ai.byak.app', serviceAccountJson: serviceAccount, productId: 'byak_pro', monthlyBasePlanId: 'monthly', yearlyBasePlanId: 'yearly', rtdnToken: 'rtdn-secret' };
const DAY = 86400000;

function fakeGoogle(state) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url: String(url), init });
    if (String(url) === 'https://oauth2.googleapis.com/token') {
      const [header, claims, signature] = new URLSearchParams(String(init.body)).get('assertion').split('.');
      assert.ok(createVerify('RSA-SHA256').update(`${header}.${claims}`).verify(publicKey, signature, 'base64url'), 'assertion must be signed by the service account key');
      assert.equal(JSON.parse(Buffer.from(claims, 'base64url')).scope, 'https://www.googleapis.com/auth/androidpublisher');
      return Response.json({ access_token: 'ya29.test', expires_in: 3600 });
    }
    assert.equal(init.headers.authorization, 'Bearer ya29.test');
    if (String(url).includes(':acknowledge')) { state.acknowledged = true; return new Response('{}'); }
    const token = decodeURIComponent(String(url).split('/tokens/')[1]);
    const purchase = state.purchases[token];
    return purchase ? Response.json(purchase()) : new Response('{}', { status: 404 });
  };
  return { fetchImpl, calls };
}

async function setup(clock = Date.now()) {
  const store = await new Store(`${process.env.BYAK_DATA_FILE}.${Math.random()}`).init();
  const state = { purchases: {}, acknowledged: false };
  const google = fakeGoogle(state); let time = clock;
  const billing = createBilling({ store, fetchImpl: google.fetchImpl, now: () => time, play });
  const purchase = (userId, { productId = 'byak_pro', basePlan = 'monthly', offerId, expiresIn = 30 * DAY, subscriptionState = 'SUBSCRIPTION_STATE_ACTIVE', ack = 'ACKNOWLEDGEMENT_STATE_PENDING', autoRenew = true, account = billing.accountIdFor(userId), linked } = {}) => () => ({
    subscriptionState, acknowledgementState: state.acknowledged ? 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' : ack, latestOrderId: 'GPA.1234', linkedPurchaseToken: linked,
    externalAccountIdentifiers: account ? { obfuscatedExternalAccountId: account } : undefined,
    lineItems: [{ productId, offerDetails: { basePlanId: basePlan, offerId }, expiryTime: new Date(time + expiresIn).toISOString(), autoRenewingPlan: { autoRenewEnabled: autoRenew } }]
  });
  return { store, state, billing, google, purchase, advance: ms => { time += ms; } };
}

const notification = (purchaseToken, notificationType = 4) => ({ message: { data: Buffer.from(JSON.stringify({ packageName: 'ai.byak.app', subscriptionNotification: { notificationType, purchaseToken, subscriptionId: 'monthly' } })).toString('base64') } });

test('verifies, acknowledges and grants a monthly subscription', async () => {
  const { billing, state, purchase } = await setup();
  state.purchases['tok-1'] = purchase('user-1');
  assert.equal(billing.entitlement('user-1').plan, 'free');
  const result = await billing.verify('user-1', 'tok-1');
  assert.equal(result.plan, 'monthly'); assert.equal(result.tier, 'pro'); assert.equal(result.autoRenewing, true);
  assert.ok(result.limits.projects > 3); assert.equal(state.acknowledged, true);
});

test('rejects purchases bound to another account or already claimed', async () => {
  const { billing, state, purchase } = await setup();
  state.purchases['tok-a'] = purchase('someone-else');
  await assert.rejects(billing.verify('user-1', 'tok-a'), { status: 403 });
  state.purchases['tok-b'] = purchase('user-1', { account: null });
  await billing.verify('user-1', 'tok-b');
  await assert.rejects(billing.verify('user-2', 'tok-b'), { status: 409 });
  await assert.rejects(billing.verify('user-1', 'unknown-token'), { status: 400 });
});

test('rejects products that are not BYAK subscriptions', async () => {
  const { billing, state, purchase } = await setup();
  state.purchases['tok-x'] = purchase('user-1', { productId: 'some_other_app_sku' });
  await assert.rejects(billing.verify('user-1', 'tok-x'), { status: 400 });
});

test('real-time notifications update status; cancelled keeps access until expiry', async () => {
  const { billing, state, purchase, advance } = await setup();
  state.purchases['tok-2'] = purchase('user-1');
  await billing.verify('user-1', 'tok-2');
  await assert.rejects(billing.handleNotification(notification('tok-2'), 'wrong'), { status: 401 });

  state.purchases['tok-2'] = purchase('user-1', { subscriptionState: 'SUBSCRIPTION_STATE_CANCELED', autoRenew: false, expiresIn: 5 * DAY });
  assert.deepEqual((await billing.handleNotification(notification('tok-2', 3), 'rtdn-secret')).status, 'cancelled');
  assert.equal(billing.entitlement('user-1').plan, 'monthly');
  advance(6 * DAY);
  assert.equal(billing.entitlement('user-1').plan, 'free');
  assert.deepEqual(await billing.handleNotification(notification('never-seen'), 'rtdn-secret'), { ignored: 'unknown_token' });
});

test('voided purchases revoke access immediately', async () => {
  const { billing, state, purchase } = await setup();
  state.purchases['tok-3'] = purchase('user-1', { basePlan: 'yearly', expiresIn: 365 * DAY });
  assert.equal((await billing.verify('user-1', 'tok-3')).plan, 'annual');
  const voided = { message: { data: Buffer.from(JSON.stringify({ packageName: 'ai.byak.app', voidedPurchaseNotification: { purchaseToken: 'tok-3' } })).toString('base64') } };
  await billing.handleNotification(voided, 'rtdn-secret');
  assert.equal(billing.entitlement('user-1').plan, 'free');
});

test('upgrades supersede the linked purchase and renewals refresh lazily', async () => {
  const { billing, state, purchase, advance, store } = await setup();
  state.purchases['old'] = purchase('user-1', { expiresIn: 10 * DAY });
  await billing.verify('user-1', 'old');
  state.purchases['new'] = purchase('user-1', { basePlan: 'yearly', expiresIn: 365 * DAY, linked: 'old' });
  assert.equal((await billing.verify('user-1', 'new')).plan, 'annual');
  assert.equal(store.data.subscriptions.find(x => x.basePlanId === 'monthly').status, 'replaced');

  // After expiry, a renewing subscription is re-checked with Google instead of silently dropping to free.
  advance(366 * DAY); state.purchases['new'] = purchase('user-1', { basePlan: 'yearly', expiresIn: 365 * DAY });
  await billing.refresh('user-1');
  assert.equal(billing.entitlement('user-1').plan, 'annual');
});

test('maps Play Console base plans and free-trial offers of byak_pro', async () => {
  const { billing, state, purchase, store } = await setup();
  state.purchases['trial'] = purchase('user-1', { offerId: 'monthly-7day-freetrail', expiresIn: 7 * DAY });
  const result = await billing.verify('user-1', 'trial');
  assert.equal(result.plan, 'monthly'); assert.equal(result.productId, 'byak_pro'); assert.equal(result.basePlanId, 'monthly');
  assert.equal(store.data.subscriptions[0].offerId, 'monthly-7day-freetrail');
  state.purchases['odd'] = purchase('user-2', { basePlan: 'weekly' });
  await assert.rejects(billing.verify('user-2', 'odd'), { status: 400 });
});
