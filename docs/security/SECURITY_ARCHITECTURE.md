# Security architecture

## Implemented controls

- AES-256-GCM credential encryption with per-record authenticated context
- Scrypt password hashing and constant-time comparison
- Short-lived signed access tokens, rotating refresh tokens with reuse detection (a replayed refresh token revokes its session) and revocation
- Constant-time login for unknown accounts (no user enumeration by timing)
- Ownership checks on conversations, projects, files, providers and devices
- Request/body/file limits, strict MIME allowlist and non-execution of uploads
- SSRF protection for the URL reader and custom provider endpoints: HTTPS only, no embedded credentials, DNS resolution checked against loopback/private/link-local/CGNAT/metadata ranges (IPv4, IPv6 and mapped), redirects refused, responses size-capped
- Built-in providers ignore base-URL overrides; local providers (Ollama) are disabled unless ALLOW_LOCAL_PROVIDERS is set
- Google Play purchases verified server-side and bound to the buying account; RTDN webhook authenticated with a shared secret
- Android: tokens encrypted with a non-exportable Android Keystore AES-GCM key
- Security headers, structured errors, rate limits and redacted audit metadata
- Provider secrets omitted from API responses, logs, source and Android artifacts
- External text explicitly isolated as untrusted RAG/tool data
- Admin role check on operational endpoints

## Production gates

Before public launch:

1. Replace the reference file store and in-process rate limiter with PostgreSQL and Redis implementations.
2. Put API behind managed TLS/WAF; allow only the real Android/API origins.
3. Use KMS envelope encryption and automatic key rotation.
4. Pin the resolved IP for outbound requests (closes the DNS-rebinding window between check and connect).
5. Run uploads through isolated malware scanning and parser workers.
6. Verify Pub/Sub push requests with OIDC tokens in addition to the shared secret.
7. Add email verification, recovery, MFA for admins and breached-password screening.
8. Add secret scanning, SAST, DAST, dependency pinning and incident alerts.
9. Complete penetration testing for IDOR, injection, token replay, upload polyglots and tool abuse.

## Threat assumptions

No application is unhackable. Provider output and retrieved content may be malicious. Tools must remain schema-constrained, permissioned and visible. BYAK AI must not execute uploaded code or follow instructions embedded in retrieved content.

Report security issues privately to the security contact published for the production domain.

