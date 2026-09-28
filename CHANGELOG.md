# Changelog

Notable repository changes are recorded here.

## 1.0.0 — production

### Play requirements
- Targets Android 16 (API 36).
- Report button on every AI answer (Google Play AI-generated content policy).
- Privacy & terms and Help & feedback in Settings; release signing from CI secrets produces a signed Play bundle.
- Google Sign-In now requires the Web application client ID and the button is hidden until it is configured.

### Fixed
- Large knowledge files were held in the main on-device data file (memory pressure, slow saves); chunks now live in
  separate files. Older installs are still read.
- An unreadable data file could be replaced by an empty one; it is now kept aside for recovery.
- The chat list re-scanned every message per conversation; it now takes one pass.
- Renaming yourself in on-device mode didn't update the Home greeting.
- A dropped or stalled connection mid-answer could hang forever or lose the partial answer.

## 0.5.0

### Fixed
- Login and "Continue with Google" failed on real phones: the app needed a BYAK server that wasn't hosted
  anywhere. BYAK now runs entirely on the phone by default, so no server is required.

### Added
- On-device mode: chats, files, projects, memory, prompts and usage stored on the phone; API keys encrypted
  with the Android Keystore; OpenAI-compatible, Anthropic and Gemini called directly (streaming, images).
- Google Sign-In that works without a server (uses the configured web client ID) and clearer errors when the
  app's SHA-1 isn't registered in Google Cloud.
- Web search in on-device mode via Wikipedia, or Brave Search with your own key.
- Pro in on-device mode comes from the Google Play purchase on the phone.

## 0.4.0

### Changed
- Billing now matches Play Console: one subscription `byak_pro` with base plans `monthly` and `yearly`
  (free-trial offers are detected automatically).
- Free plan: web search raised to 10 per day and photo questions to 12 per day.

### Added
- Compare two models side by side (Free 2 per day, Pro 100 per day).
- Monthly subscribers can switch to yearly in the app; unused time is credited by Google Play.
- Yearly plan shows its live saving ("Save 17%") and per-month price.
- One-tap follow-ups under answers: Summarize, Simplify, More detail, Translate, Continue.

## 0.3.0

### BYAK Pro
- Pro-only headroom enforced on the server: live web search in chat (Free 3/day, Pro 200/day), photo questions
  (Free 5 images/day, Pro 500/day), conversation memory (Free last 20 messages, Pro 100), document search depth
  (Free 4 excerpts, Pro 10) and saved prompts (Free 5, Pro 200).
- The app shows what's left today on the Web and photo buttons, explains exactly which limit was hit in an
  upgrade sheet, restores the blocked message, and presents a redesigned Pro screen with a Free vs Pro table.
- Free trials: if a trial offer exists in Play Console, eligible users see "Start 7-day free trial".

### Added
- Photo attachments for vision models (OpenAI, Anthropic, Gemini), downscaled on-device before upload.
- Web search in chat and automatic reading of pasted links, with clickable web citations.
- Voice input and read-aloud for answers.
- Edit and resend your last message.
- Prompt library with ten built-in templates and your own saved prompts.
- Appearance setting (system, light, dark) and Material You dynamic color.
- Configurable server address on the sign-in screen and in Settings.

### Fixed
- Release builds pointed at the emulator address over HTTP and could never connect.
- Signing in as a different account could show the previous account's chat, research and plan.
- The shared Play Billing connection was closed when a screen was destroyed, breaking purchases until the app restarted.

## 0.2.0

### Fixed
- Claude conversations failed on current models because `temperature` was always sent.
- Gemini ignored the system prompt and put the API key in the request URL.
- The Android app stopped working 15 minutes after sign-in (no token refresh).
- Android streaming violated the Flow invariant; server error events were ignored.
- Settings, Models and Projects were unreachable on phones; no way back from a chat.
- Invalid input (short passwords, malformed JSON, bad URL escapes) returned HTTP 500.
- SSRF: provider base URLs and the URL reader could reach private and metadata addresses.
- A failed disk write silently disabled persistence; the rate-limit map leaked memory.
- Project instructions, memory and (for streaming) document context were never used.
- Tokens were stored unencrypted on Android; delete account could crash the app.

### Added
- Google Play subscriptions end to end: purchase flow, server verification, acknowledgement,
  account binding, restore, renewals and real-time developer notifications; Free/Pro limits.
- Real token streaming for all providers with stop and regenerate.
- Google Sign-In, memory, custom instructions, usage stats, devices, password change, data export.
- Markdown chat rendering, model picker, conversation search/pin/archive/rename/share.
- BM25 retrieval, sentence-aware chunking, HTML/XML uploads, research summaries.
- 32 backend tests and Android unit tests.

## Unreleased

### Added
- Professional repository badges and project navigation.
- Contribution guidelines for development and review.

### Changed
- README presentation standardized around verified workflows and supported technology.
