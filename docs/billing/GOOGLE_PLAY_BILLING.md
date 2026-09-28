# Google Play subscriptions

BYAK Pro is sold as two auto-renewing Google Play subscriptions. The app never trusts a purchase on the device: every purchase token goes to the API, which checks it with Google, acknowledges it and grants the plan.

```text
Android app ──launchBillingFlow(obfuscatedAccountId)──▶ Google Play
     │ purchase token
     ▼
POST /v1/billing/google/verify ──subscriptionsv2.get──▶ Android Publisher API
     │                           ──acknowledge────────▶
     ▼
subscriptions table ◀── POST /v1/billing/google/rtdn ◀── Pub/Sub (renewals, cancellations, refunds)
```

## 1. Play Console

1. Upload a signed build of `ai.byak.app` to an internal testing track (billing only works for apps installed from Play).
2. **Monetize → Subscriptions**: create the subscription product `byak_pro`, then add active base plans `monthly` (P1M) and `yearly` (P1Y). Set prices per country.
3. **Setup → License testing**: add your test accounts so purchases are free and renew quickly.

Different identifiers? Set `PLAY_SUBSCRIPTION_PRODUCT_ID`, `PLAY_MONTHLY_BASE_PLAN_ID`, and `PLAY_ANNUAL_BASE_PLAN_ID` on the server, with the matching `BYAK_` properties for the Android build.

## 2. Server credentials

1. In Google Cloud, create a service account and a JSON key.
2. In Play Console **Users and permissions**, invite the service account email with *View financial data* and *Manage orders and subscriptions*.
3. Set `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` to the JSON (inline) or a path to the file. Until this is set, `/v1/billing/google/verify` answers `503` with an explanation and the app tells the user.

## 3. Real-time developer notifications

1. Create a Pub/Sub topic, grant `google-play-developer-notifications@system.gserviceaccount.com` the *Pub/Sub Publisher* role, and select the topic in **Monetize → Monetization setup**.
2. Generate a random secret: `openssl rand -hex 24`, set it as `GOOGLE_PLAY_RTDN_TOKEN`.
3. Create a **push** subscription to `https://<your-api>/v1/billing/google/rtdn?token=<secret>`.

Notifications only say *something changed*; the server always re-reads the subscription from Google before updating it. Voided purchases (refunds, chargebacks) revoke access immediately.

## Behaviour

| Google state | BYAK status | Pro access |
|---|---|---|
| Active | `active` | yes |
| In grace period | `grace` | yes (the app asks the user to fix payment) |
| Cancelled, not yet expired | `cancelled` | yes, until expiry |
| On hold / paused / expired | `on_hold` / `paused` / `expired` | no |
| Voided | `revoked` | no |

- **Account binding**: the app passes a per-user `obfuscatedAccountId` (an HMAC of the user id, from `/v1/subscription`). A token bought for another account is rejected with `403`; a token already linked to another account is rejected with `409`.
- **Upgrades**: when a purchase has a `linkedPurchaseToken`, the older record is marked `replaced`.
- **Renewals**: if a renewing subscription passes its expiry, the next `/v1/me` or `/v1/subscription` call re-checks Google (at most every 10 minutes) so users aren't dropped to Free while a notification is delayed.
- **Restore**: *Restore purchases* sends every subscription token the Play account owns to `/v1/billing/google/restore`.

## Testing

`backend/test/billing.test.mjs` runs the whole flow against a fake Google API, including real RS256 service-account JWTs. Run `npm test`.
