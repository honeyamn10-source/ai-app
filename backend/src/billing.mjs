import { createHash, sign } from 'node:crypto';
import { config } from './config.mjs';

let cachedAccessToken = null;
let cachedAccessTokenExpiresAt = 0;

const base64url = value => Buffer.from(value).toString('base64url');
const googleError = async response => {
  const body = await response.text();
  const error = new Error(`Google Play verification failed (${response.status}): ${body.slice(0, 500)}`);
  error.status = response.status === 404 ? 400 : 502;
  return error;
};

async function accessToken() {
  if (cachedAccessToken && Date.now() < cachedAccessTokenExpiresAt - 60_000) return cachedAccessToken;
  const credentials = config.googlePlayServiceAccount;
  if (!credentials?.client_email || !credentials?.private_key) {
    throw Object.assign(new Error('Google Play verification is not configured on the BYAK server'), { status: 503 });
  }
  const issuedAt = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = base64url(JSON.stringify({
    iss: credentials.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: credentials.token_uri || 'https://oauth2.googleapis.com/token',
    iat: issuedAt,
    exp: issuedAt + 3600,
  }));
  const unsigned = `${header}.${claims}`;
  const assertion = `${unsigned}.${sign('RSA-SHA256', Buffer.from(unsigned), credentials.private_key).toString('base64url')}`;
  const response = await fetch(credentials.token_uri || 'https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw await googleError(response);
  const token = await response.json();
  if (!token.access_token) throw Object.assign(new Error('Google OAuth response did not include an access token'), { status: 502 });
  cachedAccessToken = token.access_token;
  cachedAccessTokenExpiresAt = Date.now() + Number(token.expires_in || 3600) * 1000;
  return cachedAccessToken;
}

export function hashPurchaseToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

export async function verifyGoogleSubscription({ productId, purchaseToken }) {
  if (!config.googlePlayProducts.has(productId)) {
    throw Object.assign(new Error('Unknown BYAK subscription product'), { status: 400 });
  }
  const token = await accessToken();
  const packageName = encodeURIComponent(config.googlePlayPackageName);
  const encodedPurchaseToken = encodeURIComponent(purchaseToken);
  const response = await fetch(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/subscriptionsv2/tokens/${encodedPurchaseToken}`,
    { headers: { authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(15_000) },
  );
  if (!response.ok) throw await googleError(response);
  const purchase = await response.json();
  const line = (purchase.lineItems || []).find(item => item.productId === productId);
  if (!line) throw Object.assign(new Error('Purchase token does not belong to the requested product'), { status: 400 });

  const activeStates = new Set(['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD']);
  const expiresAtEpochMillis = Date.parse(line.expiryTime || '');
  const active = activeStates.has(purchase.subscriptionState)
    && Number.isFinite(expiresAtEpochMillis)
    && expiresAtEpochMillis > Date.now();
  return {
    verified: true,
    active,
    productId,
    basePlanId: line.offerDetails?.basePlanId || null,
    offerId: line.offerDetails?.offerId || null,
    purchaseTokenHash: hashPurchaseToken(purchaseToken),
    expiresAtEpochMillis: Number.isFinite(expiresAtEpochMillis) ? expiresAtEpochMillis : null,
    subscriptionState: purchase.subscriptionState,
    latestOrderId: line.latestSuccessfulOrderId || null,
    regionCode: purchase.regionCode || null,
    testPurchase: Boolean(purchase.testPurchase),
  };
}
