# BYAK AI — Privacy Policy

**Effective date:** 1 September 2026
**Last updated:** 1 September 2026
**Application:** BYAK AI (Google Play) — package `ai.byak.app`
**Operator:** Bittu Sharma
**Contact:** [byakai@yahoo.com](mailto:byakai@yahoo.com)
**Address for formal notices:** 55 Pioneer Lane, Vaughan, Ontario L4L 2J2, Canada

---

## 1. Summary

BYAK AI is a **bring-your-own-key** AI assistant. It does not sell you an AI subscription and
it does not run a cloud account for you. The app runs on your phone, and BYAK AI's servers are
not in the path of your conversations.

The short version:

- Your chats, files, projects, memory, prompts, settings and API keys are created and stored
  **on your device**.
- BYAK AI's developers **do not receive** your prompts, your photos, your files or your API keys.
- When you ask a question, the app sends the request **directly from your phone to the AI
  provider you chose**, using the API key you entered. That provider's own privacy policy
  governs what happens next.
- Purchases are handled entirely by **Google Play**. BYAK AI never sees your card details.
- You can erase everything BYAK AI stored on your phone at any time, in **Settings**.

## 2. Who this policy covers

This policy covers the BYAK AI Android application distributed through Google Play, and the
BYAK AI public website.

It does not cover third-party services that BYAK AI does not control. When you connect an AI
provider, a search service, or sign in with Google, that service has its own privacy policy and
you are subject to it as well. Section 5 lists the specific services the app can contact.

## 3. Information stored on your device

The app creates the following data **locally on your device**. This data is not transmitted to
the BYAK AI developer:

| Data | Where it lives | Why |
|---|---|---|
| Conversations and messages | App-private storage | To show your chat history |
| Projects, memory and saved prompts | App-private storage | Organising your work |
| Attached files and extracted text | App-private storage | Answering questions about your documents |
| Photos you attach | App-private memory, then sent to your chosen provider for that request | Answering a question about an image |
| App settings and appearance | App-private storage | Remembering your choices |
| Daily usage counters | App-private storage | Enforcing free-plan limits |
| Subscription status | Read from Google Play on your device | Granting BYAK Pro |

### 3.1 Provider API keys

API keys you add are stored **only on your device** and are encrypted at rest using the
**Android Keystore**, a hardware-backed system facility. The keys are never transmitted to the
BYAK AI developer and are never included in the code of the app.

Each request to an AI provider is authenticated with the relevant key, so **that provider
necessarily receives the key** in order to serve the request. This is direct, device-to-provider
communication.

### 3.2 Android backup

The app disables Android's automatic backup for its protected application data where the
platform allows it. Where you have system-level device backup (for example a device-to-device
transfer) enabled, the operating system may move app data between your own devices. That
transfer is governed by your device settings and your device manufacturer's policy, not by this
one.

## 4. Information BYAK AI's developers do not collect

BYAK AI's developers operate **no server-side account system for the app**. In particular, the
developer does not collect or store:

- the content of your messages or prompts;
- the photos, files or documents you attach;
- your AI provider API keys;
- your name, email address or phone number (unless you choose to sign in with Google, in which
  case the identity is read on your device — see section 5.4);
- your contacts, call logs, SMS, photos library, precise or coarse location;
- your advertising ID;
- microphone, camera, or sensor data in the background;
- clipboard contents, other than when you explicitly paste into the app.

## 5. Information sent to third parties, and when

Every transfer below happens **only after you take an action in the app**, over an encrypted
HTTPS connection, and only to the service you selected. BYAK AI does not add its own servers to
these paths.

### 5.1 AI providers

If you connect a provider and send a message, the app sends the request from your phone
directly to that provider. The transfer contains the text of your prompt, the conversation
context the app includes, any images you attached, and your chosen model. It also contains the
API key for that provider, which the provider needs in order to authenticate the call.

Providers the app can be configured to call:

| Provider | Endpoint |
|---|---|
| OpenAI | `api.openai.com` |
| Anthropic | `api.anthropic.com` |
| Google Gemini | `generativelanguage.googleapis.com` |
| OpenRouter | `openrouter.ai` |
| Groq | `api.groq.com` |
| Mistral AI | `api.mistral.ai` |
| DeepSeek | `api.deepseek.com` |
| Any custom OpenAI-compatible endpoint you enter | as you specify |

**Cost and terms.** These providers bill you directly through your own API key. BYAK AI does not
pay for, proxy, resell or bundle third-party inference. BYAK Pro does **not** include unlimited
or free third-party model usage — it only raises the in-app limits listed in section 8. Your use
of each provider is governed by that provider's terms of service and privacy policy, including
its own retention practices, training practices and retention periods, over which BYAK AI has no
control.

### 5.2 Web search and research

When you use a search or research feature, the app sends your search query from your phone
directly to the search service you selected:

- **Wikipedia** — the default; used when you have not added a search key. No key required.
- **Brave Search** — used when you have added your own Brave Search API key. That key is sent to
  Brave to authenticate the query.
- **GitHub** — repository search via the public GitHub API. A personal access token is sent to
  GitHub only if you have added one.
- **Reddit** — public search only, via Reddit's public JSON endpoints.

When you open a result, the app fetches that page's text from its own host so the model can read
it. Only `https://` URLs are accepted.

### 5.3 Google Play billing

Subscription purchases, renewals, refunds and cancellations are created, processed and stored by
**Google Play**, under Google's privacy policy. BYAK AI receives only what the Play Billing
Library exposes on your device in order to decide whether to grant BYAK Pro — for example the
product identifier, base plan, purchase state and purchase token. **BYAK AI never receives or
stores your payment card details, billing address or full payment credentials.**

### 5.4 Google Sign-In (optional)

If you choose "Continue with Google", the app opens Google's own sign-in flow. The name and
email address in your Google profile are read **on your device** and are not transmitted to the
BYAK AI developer. Skipping sign-in leaves the app fully usable.

### 5.5 Email

The "Report this response" button in the app opens **your own email app** with a message
pre-addressed to [byakai@yahoo.com](mailto:byakai@yahoo.com). Nothing is sent until you
press send in your email app. The same applies to "Help & feedback" in Settings.

## 6. The BYAK AI website

The public BYAK AI website is separate from the app and does collect a small amount of data that
you actively submit:

- **Launch list** — an email address, if you choose to join the launch list, used only to send
  product announcements.
- **Support form** — the name, email address and message you type, used only to answer you.
- **Security logs** — limited technical data (such as IP address and request metadata) may be
  processed by the hosting provider to prevent abuse and keep the site available.

Website submissions are retained only as long as reasonably necessary to answer you, to meet
legal obligations, and to keep the service secure. You can ask for deletion at any time using the
address in section 10.

## 7. Retention and deletion

| What | Retention |
|---|---|
| App data on your device | Until you delete it, or uninstall the app |
| Data sent to an AI provider | That provider's own policy |
| Data sent to a search service | That service's own policy |
| Play purchase records | Google's policy |
| Launch-list and support emails | As long as needed to respond and to meet legal obligations |

### 7.1 Erasing your data

- **In the app:** **Settings → Erase all BYAK data on this phone**. This clears conversations,
  files, projects, memory, prompts, settings and stored keys on the device.
- **By uninstalling:** removing the app removes its private data. If you want to keep BYAK Pro,
  reinstall from Google Play and restore purchases.
- **On the website:** email the address in section 10 to request deletion of launch-list or
  support records.

### 7.2 Uninstalling does not cancel a subscription

**Uninstalling BYAK AI, or erasing its data, does not cancel a Google Play subscription.** To
stop renewals, cancel in **Google Play → Settings → Subscriptions → BYAK Pro**, or in the app
via your Google Play account. Deleting local data while a subscription is active leaves the
subscription active and renewing.

## 8. BYAK Pro

The app is free to download. **BYAK Pro** is an optional subscription sold through Google Play
that raises in-app limits, such as:

- more daily web searches;
- more photo questions;
- more model comparisons;
- longer conversation context;
- more projects and knowledge files;
- more saved prompts and memories.

BYAK Pro does not include third-party model usage. You continue to pay your AI provider directly
for the inference you request.

## 9. Security

The app protects your data with:

- **Android Keystore** encryption for stored provider API keys;
- **app-private storage** for chats, files and settings, isolated from other apps;
- **HTTPS/TLS for every network transfer** the app makes;
- **no server-side storage** of your conversations, files or keys.

Limitations: no method of storage or transmission is completely secure. A rooted, malware-infected
or otherwise compromised device, or a device with a weak screen lock, can expose local data. Your
own provider accounts are governed by the provider's security practices. BYAK AI's website uses
HTTPS and standard web hardening headers, but no system is guaranteed to be free of
vulnerabilities.

## 10. Children's privacy

**BYAK AI is intended for adults aged 18 and over** and is not directed at children. The app
relies on third-party AI models whose output is open-ended and unfiltered, and it is not designed
to meet children's online-safety requirements. If you are under 18, do not use BYAK AI. If you
believe a child has provided personal information to BYAK AI's developer, contact us at the
address below so it can be deleted.

## 11. Your rights

Depending on where you live, you may have rights to access, correct, export, delete or restrict
processing of your personal information, to object to processing, to data portability, and to
complain to a data protection authority.

Because app data stays on your device, the fastest way to exercise these rights for app content
is to erase it in Settings (section 7.1). For website records, or for anything else, contact us
and we will respond within a legally required period, generally within 30 days. We do not sell
personal information, and we do not share it for cross-context behavioural advertising.

## 12. International transfers

AI and search providers named in section 5 are global companies. If you use them, your prompt,
and any attachment, may be processed in a country other than your own. Those transfers are
governed by the provider's own safeguards. Website support data may also be processed outside
your country by the hosting provider.

## 13. Changes to this policy

If this policy changes materially, the "Last updated" date above changes and, for significant
changes, the app will show a notice. Continuing to use BYAK AI after a change means you accept
the updated policy.

## 14. Contact

**BYAK AI — Privacy**
Bittu Sharma
55 Pioneer Lane, Vaughan, Ontario L4L 2J2, Canada
Email: [byakai@yahoo.com](mailto:byakai@yahoo.com)

Use this address for privacy questions, data requests, and to report a security issue.
