# Production release checklist (Google Play)

Application id `ai.byak.app`, `targetSdk` 36.

> ### Read this before building anything
>
> **This repository does not have one monotonic version line.** `main` is the on-device
> rewrite and sits at `versionCode 6` / `versionName 1.0.0`, but Google Play already holds
> builds from the other line up to **`versionCode 34` / `3.0.1`**
> (`release/byak-v3.0.1-code34-loginfix`). The two have diverged — `020bb3f` (code 34) is
> **not** an ancestor of `main`, and `main` has 6 commits the release branch does not.
>
> Consequences:
>
> - A bundle built from `main` as-is is `versionCode 6`. **Play will reject it.** This branch
>   ships `versionCode 35` / `versionName 3.1.0` so it is both numerically above the 34 already
>   on Play and visibly above `3.0.1` in the store listing. Raise both after every upload.
> - The **Publish to Google Play** workflow enforces the numeric floor. Set the repository
>   variable `BYAK_MIN_VERSION_CODE` to the highest versionCode Play has already accepted — `34`
>   for now — and raise it after every successful upload. The build fails rather than uploading
>   a duplicate or lower versionCode.
> - Decide deliberately which line you are shipping. `main` is the newer architecture
>   (runs entirely on the phone, no BYAK server). The `release/byak-v3.0.1-*` branches are the
>   older server-backed line that produced the builds already on Play.

Two workflows exist:

| Workflow | Trigger | What it does |
|---|---|---|
| **BYAK CI** | every push and PR | backend tests, Android lint + unit tests, debug APK, SBOM, Trivy; and a signed `BYAK-AI-release-aab` once the signing secrets exist |
| **Publish to Google Play** | manual (`workflow_dispatch`) | validates signing, builds a signed AAB, keeps it as an artifact, uploads it to a Play track |

---

## 0. The upload key (do this first, and back it up twice)

The upload key signs every build sent to Play. If it is lost you cannot ship an update until
Google resets it for you, which takes days.

```bash
keytool -genkeypair -v -keystore upload.jks -alias byak-upload \
        -keyalg RSA -keysize 4096 -validity 10000
keytool -list -v -keystore upload.jks -alias byak-upload   # record the SHA-256
```

Back up, **outside the repository**, in at least two places:

- `upload.jks`
- the keystore and key passwords, in a password manager
- `BYAK-AI-upload-certificate.pem` (public, safe to register with Google)
- the certificate fingerprints
- `base64 -w0 upload.jks` (this is what goes into the GitHub secret)

`.gitignore` already excludes `*.jks` and `*.keystore`. Never commit the keystore or its
passwords.

## 1. GitHub secrets

Settings → Secrets and variables → Actions → **Secrets**:

| Secret | Value |
|---|---|
| `BYAK_UPLOAD_KEYSTORE_BASE64` | `base64 -w0 upload.jks`, one line, no quotes, no trailing spaces |
| `BYAK_KEYSTORE_PASSWORD` | keystore (store) password |
| `BYAK_KEY_ALIAS` | `byak-upload` |
| `BYAK_KEY_PASSWORD` | private key password |
| `BYAK_PLAY_SERVICE_ACCOUNT_JSON` | the complete Google Cloud service-account JSON, for Play publishing only |

Verify locally before pushing, using the real key:

```bash
BYAK_UPLOAD_KEYSTORE_BASE64="$(base64 -w0 upload.jks)" \
BYAK_KEYSTORE_PASSWORD='…' BYAK_KEY_ALIAS=byak-upload BYAK_KEY_PASSWORD='…' \
  ./scripts/validate-release-signing.sh
```

The script checks that all four values are present, that the payload is really a JKS, that the
store password opens it, that the alias holds a private key, that the key password unlocks it,
and that the certificate matches the fingerprint you expect. CI runs the same script before
every release build, and fails the build rather than shipping an unsigned or wrong-key bundle.

## 2. GitHub variables

Settings → Secrets and variables → Actions → **Variables**:

| Variable | Value | Why |
|---|---|---|
| `BYAK_SUPPORT_EMAIL` | `byakai@yahoo.com` | where "Report response" and "Help & feedback" go. Required by Play's AI-content policy. |
| `BYAK_PRIVACY_POLICY_URL` | `https://honeyamn10-source.github.io/honeyamn10-source/byak-ai/privacy/` | Play store listing + the in-app link. **Already set and live.** |
| `BYAK_TERMS_URL` | `https://honeyamn10-source.github.io/honeyamn10-source/byak-ai/terms/` | in-app terms link. Already set and live. |
| `BYAK_UPLOAD_CERT_SHA256` | `76:A5:13:51:EC:A1:53:AF:49:C2:D6:20:26:C6:6A:D8:43:5B:F1:02:3B:15:54:93:C1:1B:68:2D:A8:4F:02:94` | pins the upload key; the build fails on a mismatch |
| `BYAK_MIN_VERSION_CODE` | `34` to start, then the highest code Play has accepted | the workflow refuses to upload a duplicate or lower versionCode |
| `BYAK_GOOGLE_WEB_CLIENT_ID` | optional override | the app already embeds the Web client `1077439001893-rnboa31…` from project `byak-ai`. Never put a client *secret* in the app. |

`BYAK_PRIVACY_POLICY_URL` and `BYAK_TERMS_URL` are injected into `BuildConfig` and the Settings
links are **hidden entirely when the value is empty** — so if you forget these, the shipped app
has no privacy policy link at all.

## 3. Publishing the policy

`docs/legal/PRIVACY_POLICY.md` and `docs/legal/TERMS.md` are the single source of truth. Render
them to standalone HTML:

```bash
python3 scripts/build-legal-pages.py          # writes site/legal/privacy.html and terms.html
```

The build **fails** while any `[[...]]` operator placeholder is unresolved, because Play rejects
a policy with no operator name or address. The operator details are filled in:

| Field | Value |
|---|---|
| Legal name | Bittu Sharma |
| Postal address | 55 Pioneer Lane, Vaughan, Ontario L4L 2J2, Canada |
| Governing jurisdiction | Ontario, Canada |
| Contact | byakai@yahoo.com |

The rendered pages are published from the `honeyamn10-source` profile repository under
`website/byak-ai/`, which GitHub Pages serves at:

- <https://honeyamn10-source.github.io/honeyamn10-source/byak-ai/privacy/>
- <https://honeyamn10-source.github.io/honeyamn10-source/byak-ai/terms/>

Both return 200 with no login and no cookie wall, which is what Play requires. Re-render with
`python3 scripts/build-legal-pages.py`, copy the output to `website/byak-ai/{privacy,terms}/index.html`,
and push to trigger the Pages deploy.

`byak.site.je` cannot be used: it resolves to an old host that answers every path with a redirect
to `https://www.safebrowse.io/warn.html` and fails the TLS handshake, so it serves no policy.

Check the result:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  https://honeyamn10-source.github.io/honeyamn10-source/byak-ai/privacy/   # must be 200
```

## 4. Google Sign-In (Google Cloud project `byak-ai`)

1. **Android** client for package `ai.byak.app` with the SHA-1 of the **Play App Signing**
   certificate (Play Console → Test and release → App integrity → App signing).
2. Add a second Android client for the **upload key** SHA-1 if you install builds outside Play.
3. **Web application** client `1077439001893-rnboa31frshmo5iopmkbvasd41lv8hqe` is built in.
4. Publish the OAuth consent screen, or only test users can sign in.

## 5. Billing

- Subscription `byak_pro` with base plans `monthly` ($1) and `yearly` ($10), auto-renewing.
- Optional free-trial offer under each base plan. The app detects trials by their free pricing
  phase and shows "Start free trial" to eligible users.
- Fill in the subscription **benefits** shown on the Play purchase sheet.
- Add your account under **Setup → License testing** so purchases are free and renew quickly.
- In on-device mode Pro is granted from the Play purchase on the phone
  (`LocalApi.kt` → `BillingManager.acknowledge`), so the Android Publisher API service account
  is **not** required for the app itself. It is only needed by the optional self-hosted backend
  (`docs/billing/GOOGLE_PLAY_BILLING.md`).
- **Publishing is a separate concern.** The *Publish to Google Play* workflow needs a service
  account with the **Release Manager** role on Play Console → Users and permissions, granted the
  scope "See and perform actions against releases" (`androidpublisher`). Save its JSON and set it
  as the `BYAK_PLAY_SERVICE_ACCOUNT_JSON` secret. Add the service account address in Play Console →
  Users and permissions → Invite users, or the upload is rejected. This is the only Play credential
  still missing.

### Verified end to end on 2026-09-28

A real run of the workflow on `main` (`36405309704`) confirms the build job passes completely:
signing validation, the `versionCode 35 > 34` floor, and signer check against
`76:A5:…:4F:02:94`. The upload job installs fastlane 2.240.1 and then stops at the service
account check above, which is the intended behaviour with no credential configured.

Two bugs were only found by running it, not by validating the YAML:
`ruby/setup-ruby` failed because the repo had no `.ruby-version`, and the versionCode floor
exited 0 when `BYAK_MIN_VERSION_CODE` was unset. Both are fixed.

## 6. Store listing and policy forms

- **Privacy policy URL** — the page from step 3, not the homepage.
- **Data safety** (on-device mode): data is stored on the device and not collected by the
  developer; user-directed transfer of messages, photos and files to the AI provider the user
  configured with their own key, declared as *App activity → messages* and *Photos*, shared for
  app functionality, user-initiated, encrypted in transit; research queries to the user's chosen
  search service; Google Sign-In reads name and email on device only; purchases handled by
  Google Play; no advertising ID, contacts or location.
- **AI-generated content**: declare it, and point at the in-app **Report** button on every answer.
- **Content rating**: open-ended AI chat with user-provided content.
- **Target audience**: 18+.
- **Account deletion**: Settings → "Erase all BYAK data on this phone". No server account is
  created. State clearly that uninstalling does **not** cancel a Play subscription.

## 7. Release

1. **Internal testing.** Actions → *Publish to Google Play* → Run workflow, with
   `track: internal`. `rollout_fraction` is ignored for internal.
2. Install from the Play **internal testing invitation link**, not from a shared APK — billing
   only works for Play-installed builds.
3. Work through the test pass below.
4. **Production.** Run the workflow again with `track: production` and `rollout_fraction: 0.1`.
   The production lane sends `status: inProgress` with that fraction, so Google runs a 10% staged
   rollout. Increase the fraction with the `set_rollout` lane, or re-run with a higher value.
5. Watch crashes, ANRs, reviews, purchases and refunds before widening.

The signed bundle is always at `android/app/build/outputs/bundle/release/app-release.aab` and is
retained for 90 days as the `BYAK-AI-release-aab` workflow artifact.

## 8. Test pass on the Play-installed build

- [ ] First launch opens straight into the app — no sign-in or sign-up screen
- [ ] Add a key for each supported provider
- [ ] Start, stop, regenerate, edit, copy and share a chat
- [ ] Photos and files
- [ ] Web research
- [ ] Buy the $1 monthly plan, then restore purchases
- [ ] Switch monthly → yearly
- [ ] Uninstall and reinstall; Pro is restored under the same Play account
- [ ] The report button opens an email to `BYAK_SUPPORT_EMAIL`
- [ ] The privacy-policy link opens the published policy
- [ ] Google's pre-launch report and Android vitals show nothing serious
