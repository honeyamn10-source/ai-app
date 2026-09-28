# Changelog

Notable repository changes are recorded here.

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
