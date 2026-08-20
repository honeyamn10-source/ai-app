# Repository audit

Reviewed 2026-08-20. This is an architectural reference audit; no source from these applications was copied into BYAK AI.

| Project | Current license signal | Decision | Useful reference | Risk |
|---|---|---|---|---|
| [LibreChat](https://github.com/danny-avila/LibreChat) | Repository license is MIT | REFERENCE ONLY | Provider abstraction, multi-user chat, MCP concepts | Large dependency/transitive-license surface; audit every package before reuse |
| [AnythingLLM](https://github.com/Mintplex-Labs/anything-llm) | Core identifies as MIT | REFERENCE ONLY | Document ingestion, workspace isolation, local model UX | Desktop/server architecture does not map directly to native Android |
| [Open WebUI](https://github.com/open-webui/open-webui) | Multi-license history and current branding conditions | DO NOT USE without legal review | Local-model UX concepts only | Branding and contribution-date license boundaries complicate commercial reuse |
| [Ollama](https://github.com/ollama/ollama) | Check exact release/dependencies before integration | ADAPT protocol only | Local OpenAI-compatible endpoint | LAN security, model licenses and mobile resource limits |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT repository; models remain separately licensed | FUTURE ADAPT | Native/on-device inference engine | APK size, ABI support, thermals, RAM and model redistribution rights |

## Decisions

- BYAK AI uses an original Kotlin client and original Node reference API.
- No full repository is vendored or merged.
- Open WebUI code and branding are excluded.
- Protocol compatibility is preferred over copying provider or local-model clients.
- Any future reused file must retain its copyright/license and be entered into the release SBOM/notices.

## Google Play billing review

The build uses Play Billing Library 9.1.0, the current version documented during this audit. Google requires server verification before entitlement, handling of purchase states, acknowledgement, restore/query flows, and test-track/license-tester validation. BYAK AI's backend intentionally refuses to grant premium access until the Play service account and production product configuration exist.

