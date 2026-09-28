# Production release checklist (Google Play)

Version 1.0.0 (versionCode 6), `targetSdk` 36.

## 1. Signing (one time)
1. Create an upload key: `keytool -genkeypair -v -keystore upload.jks -alias byak-upload -keyalg RSA -keysize 4096 -validity 10000`
2. Keep `upload.jks` and its passwords somewhere safe — **never commit them**.
3. GitHub → Settings → Secrets and variables → Actions → **Secrets**:
   - `BYAK_UPLOAD_KEYSTORE_BASE64` = output of `base64 -w0 upload.jks`
   - `BYAK_KEYSTORE_PASSWORD`, `BYAK_KEY_ALIAS` (`byak-upload`), `BYAK_KEY_PASSWORD`
4. CI then publishes a signed **BYAK-AI-release-aab** artifact on every push. Upload that `.aab` to Play.
5. Keep **Play App Signing** on (default). Play re-signs with its own app-signing key.

## 2. App configuration (GitHub → Actions → **Variables**)
| Variable | Why |
|---|---|
| `BYAK_GOOGLE_WEB_CLIENT_ID` | Google Sign-In. Must be the **Web application** OAuth client (see below). The button stays hidden until it is set. |
| `BYAK_SUPPORT_EMAIL` | Where "Report response" and "Help & feedback" emails go. Needed for Play's AI-content policy. |
| `BYAK_PRIVACY_POLICY_URL` | Privacy policy link in Settings (also required in the store listing). |
| `BYAK_TERMS_URL` | Optional terms of service link. |

## 3. Google Sign-In (Google Cloud project `byak-ai`)
1. **Android client**: package `ai.byak.app`, SHA-1 = Play Console → Test and release → App integrity → **App signing key certificate**. Add a second Android client with the **upload key** SHA-1 if you install builds outside Play.
2. **Web application client**: create one (no redirect URIs needed) and put its Client ID in `BYAK_GOOGLE_WEB_CLIENT_ID`.
3. OAuth consent screen: app name, support email, logo, and **publish** it (otherwise only test users can sign in).

## 4. Billing
- Subscription `byak_pro` with base plans `monthly` ($1) and `yearly` ($10), each with a free-trial offer. **Activate** them.
- Fill in the subscription **Benefits** (shown on the Play purchase sheet).
- Test with license testers before release: buy, cancel, restore, monthly → yearly switch.

## 5. Store listing & policy forms
- **Privacy policy URL** (template: `docs/legal/PRIVACY_POLICY_TEMPLATE.md`).
- **Data safety** (on-device mode):
  - Data is stored on the device and not collected by the developer.
  - User-directed transfer: messages, photos and files are sent to the AI provider the user configures with their own key. Declare *App activity → messages* and *Photos* as shared, for app functionality, user-initiated, encrypted in transit.
  - Google Sign-In reads name and email on the device only (not sent to the developer).
  - Purchases are handled by Google Play.
- **AI-generated content**: the app has an in-app **Report** (flag) button on every AI answer.
- **Content rating** questionnaire: AI chat with user-generated content.
- **Target audience**: 18+ recommended (third-party AI models, open-ended content).
- **Account deletion**: Settings → "Erase all BYAK data on this phone". No server account is created in on-device mode.

## 6. Before you press "Release"
- [ ] Install the signed build from the internal track and try every screen on a real phone
- [ ] Google Sign-In works on the Play-installed build
- [ ] Add a provider key, chat, stop, regenerate, photos, web search, compare, files
- [ ] Purchase and restore Pro with a license tester; free limits show and reset
- [ ] Report button opens email to `BYAK_SUPPORT_EMAIL`
- [ ] Pre-launch report in Play Console shows no crashes
- [ ] Roll out to production at a staged percentage (e.g. 10%) and watch Android vitals
