# License matrix

| Component | Use | Classification | Release action |
|---|---|---|---|
| Original BYAK AI source | Shipped | Proprietary | Add owner copyright/license text |
| AndroidX / Compose / Material | Shipped dependency | Generally Apache-2.0 | Generate exact notices from locked graph |
| Kotlin / coroutines | Shipped dependency | Apache-2.0 | Preserve notices |
| OkHttp | Shipped dependency | Apache-2.0 | Preserve notices |
| Google Play Billing / Identity | Shipped SDK | Provider terms + SDK license | Review current SDK terms and Data Safety |
| Node.js | Runtime | Node.js license | Preserve runtime/container notices |
| PostgreSQL / pgvector | Infrastructure | PostgreSQL / PostgreSQL-style | Preserve notices |
| Redis | Infrastructure | Review exact selected image/version | Pin version and audit before release |
| MinIO | Optional infrastructure | AGPL/commercial considerations | Legal review or replace with managed S3 |
| LibreChat | Not shipped | MIT repository signal | Reference only |
| AnythingLLM | Not shipped | MIT core signal | Reference only |
| Open WebUI | Not shipped | Multi-license/branding conditions | Do not use without legal review |

This matrix is not legal advice. Release CI must generate an SBOM and license report from exact dependency versions and container digests.

