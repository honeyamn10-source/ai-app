#!/usr/bin/env bash
#
# Validate the Google Play release-signing configuration before a build.
#
# Verifies that all four required values are present, that the base64 payload
# really is a JKS keystore, that the keystore password opens it, that the
# configured alias exists, that it holds a private key entry, that the key
# password actually unlocks that key, and that the signing certificate matches
# the expected BYAK upload certificate.
#
# Secrets are never printed. Every temporary file is removed on exit.
#
# Environment (supplied by GitHub Actions secrets):
#   BYAK_UPLOAD_KEYSTORE_BASE64   base64 of upload.jks, no line breaks
#   BYAK_KEYSTORE_PASSWORD        keystore (store) password
#   BYAK_KEY_ALIAS                key alias, expected to be "byak-upload"
#   BYAK_KEY_PASSWORD             private key password
#
# Optional:
#   BYAK_KEYSTORE_FILE            pre-decoded keystore path; skips base64 decode
#   EXPECTED_CERT_SHA256          pin the certificate fingerprint (colon-hex)
#   KEYTOOL                       override the keytool binary

set -euo pipefail

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
step() { printf '==> %s\n' "$*"; }

KEYTOOL="${KEYTOOL:-keytool}"
command -v "$KEYTOOL" >/dev/null 2>&1 || fail "keytool not found. Install a JDK (17 or newer) or set KEYTOOL."

TMPDIR_SIG="$(mktemp -d)"
cleanup() {
  # Overwrite before unlinking so the decrypted key does not linger on disk.
  if [ -f "$TMPDIR_SIG/upload.jks" ]; then
    chmod -u+w "$TMPDIR_SIG/upload.jks" 2>/dev/null || true
    dd if=/dev/urandom of="$TMPDIR_SIG/upload.jks" bs=1 count=4096 conv=notrunc status=none 2>/dev/null || true
  fi
  rm -rf "$TMPDIR_SIG"
}
trap cleanup EXIT
chmod 700 "$TMPDIR_SIG"

# ---------------------------------------------------------------- required ---
step "Checking required secrets are present"
missing=0
for name in BYAK_KEYSTORE_PASSWORD BYAK_KEY_ALIAS BYAK_KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then
    printf 'ERROR: secret %s is empty.\n' "$name" >&2
    missing=1
  fi
done
if [ -z "${BYAK_UPLOAD_KEYSTORE_BASE64:-}" ] && [ -z "${BYAK_KEYSTORE_FILE:-}" ]; then
  printf 'ERROR: secret BYAK_UPLOAD_KEYSTORE_BASE64 is empty (and BYAK_KEYSTORE_FILE is unset).\n' >&2
  missing=1
fi
[ "$missing" -eq 0 ] || fail "Refusing to build an unsigned release. Add the missing secrets in GitHub -> Settings -> Secrets and variables -> Actions -> Secrets."

# ----------------------------------------------------------------- keystore ---
if [ -n "${BYAK_KEYSTORE_FILE:-}" ]; then
  step "Using pre-decoded keystore at BYAK_KEYSTORE_FILE"
  [ -r "$BYAK_KEYSTORE_FILE" ] || fail "BYAK_KEYSTORE_FILE is not readable: $BYAK_KEYSTORE_FILE"
  cp "$BYAK_KEYSTORE_FILE" "$TMPDIR_SIG/upload.jks"
else
  step "Decoding BYAK_UPLOAD_KEYSTORE_BASE64"
  printf '%s' "$BYAK_UPLOAD_KEYSTORE_BASE64" | tr -d '\n\r' | base64 --decode > "$TMPDIR_SIG/upload.jks" 2>/dev/null \
    || fail "BYAK_UPLOAD_KEYSTORE_BASE64 is not valid base64."
  [ -s "$TMPDIR_SIG/upload.jks" ] || fail "BYAK_UPLOAD_KEYSTORE_BASE64 decoded to an empty file."
  chmod 600 "$TMPDIR_SIG/upload.jks"
fi

KS="$TMPDIR_SIG/upload.jks"

step "Confirming the decoded file is a JKS keystore"
# Check the JKS magic directly: keytool reports a wrong password and a non-keystore
# file with different messages, but only on stdout, so read both streams.
MAGIC="$(od -An -tx1 -N4 "$KS" 2>/dev/null | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
if [ "$MAGIC" != "FEEDFEED" ]; then
  fail "BYAK_UPLOAD_KEYSTORE_BASE64 does not decode to a JKS keystore (JKS magic was '$MAGIC', expected 'FEEDFEED').
The secret is most likely a PKCS12/PKCS8 file, a truncated payload, or base64 with line breaks or extra characters.
Regenerate it with: base64 -w0 BYAK-AI-upload-key.jks"
fi
echo "    JKS magic OK (FEEDFEED)"

# keytool prints its errors on stdout, so merge the streams.
if ! "$KEYTOOL" -list -keystore "$KS" -storepass "$BYAK_KEYSTORE_PASSWORD" >"$TMPDIR_SIG/list.txt" 2>&1; then
  if grep -qi "password was incorrect\|tampered with" "$TMPDIR_SIG/list.txt"; then
    fail "BYAK_KEYSTORE_PASSWORD does not open the keystore. Check for stray quotation marks or trailing spaces in the secret."
  fi
  if grep -qi "Unrecognized keystore format" "$TMPDIR_SIG/list.txt"; then
    fail "The file has a JKS header but keytool will not read it. It is truncated or corrupted."
  fi
  fail "keytool could not read the keystore. Either BYAK_KEYSTORE_PASSWORD is wrong, or the file is not a keystore."
fi
grep -q "Keystore type: JKS" "$TMPDIR_SIG/list.txt" || fail "The decoded file is not a JKS keystore."
echo "    keystore opens, type JKS"

# -------------------------------------------------------------------- alias ---
step "Checking the key alias '$BYAK_KEY_ALIAS'"
grep -q "^${BYAK_KEY_ALIAS}," "$TMPDIR_SIG/list.txt" \
  || fail "Alias '$BYAK_KEY_ALIAS' is not in the keystore. Available entries: $(grep -oE '^[A-Za-z0-9_.-]+,' "$TMPDIR_SIG/list.txt" | tr -d ',' | tr '\n' ' ')"
grep "^${BYAK_KEY_ALIAS}," "$TMPDIR_SIG/list.txt" | grep -q "PrivateKeyEntry" \
  || fail "Alias '$BYAK_KEY_ALIAS' is not a PrivateKeyEntry. There is no private key to sign with."
echo "    alias present and holds a private key"

# -------------------------------------------------------------- key password ---
step "Checking BYAK_KEY_PASSWORD unlocks '$BYAK_KEY_ALIAS'"
"$KEYTOOL" -importkeystore \
  -srckeystore "$KS" -srcstorepass "$BYAK_KEYSTORE_PASSWORD" \
  -srcalias "$BYAK_KEY_ALIAS" -srckeypass "$BYAK_KEY_PASSWORD" \
  -destkeystore "$TMPDIR_SIG/probe.p12" -deststoretype PKCS12 \
  -deststorepass "probe-$BYAK_KEY_ALIAS" >"$TMPDIR_SIG/keypass.txt" 2>&1 \
  || fail "BYAK_KEY_PASSWORD did not unlock the private key '$BYAK_KEY_ALIAS'.
Either BYAK_KEY_PASSWORD is wrong, or this alias was created with a key password different from the keystore password.
Gradle needs BYAK_KEY_PASSWORD even when it equals BYAK_KEYSTORE_PASSWORD."
echo "    key password accepted"

# ----------------------------------------------------------------- cert pin ---
step "Reading the signing certificate"
FP="$("$KEYTOOL" -list -v -keystore "$KS" -storepass "$BYAK_KEYSTORE_PASSWORD" -alias "$BYAK_KEY_ALIAS" 2>/dev/null \
  | grep -i "SHA256:" | head -1 | sed 's/.*SHA256: *//' | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
[ -n "$FP" ] || fail "Could not read the certificate fingerprint for '$BYAK_KEY_ALIAS'."
echo "    certificate SHA-256: $FP"

if [ -n "${EXPECTED_CERT_SHA256:-}" ]; then
  EXPECTED="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d '[:space:]' | tr '[:lower:]' '[:upper:]')"
  if [ "$FP" != "$EXPECTED" ]; then
    fail "Certificate mismatch. Expected $EXPECTED but the keystore holds $FP.
Google Play rejects a build signed with an upload key the app was never registered with.
If this is a genuine key rotation, reset the upload key in Play Console first."
  fi
  echo "    fingerprint matches the expected BYAK upload certificate"
else
  echo "    (set EXPECTED_CERT_SHA256 as a repository variable to pin this)"
fi

rm -f "$TMPDIR_SIG/probe.p12"
printf '\nRelease signing configuration is valid.\nKeystore: JKS | Alias: %s | Cert: %s\n' "$BYAK_KEY_ALIAS" "$FP"
