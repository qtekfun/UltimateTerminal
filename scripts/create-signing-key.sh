#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#
# One-time setup of the release signing key (see RELEASING.md): creates the keystore outside the
# repository, uploads the four UT_* secrets the Release workflow reads, and prints the certificate
# fingerprint F-Droid needs. The passwords are typed here and never written to disk or to a log.
set -euo pipefail

REPO="${UT_REPO:-qtekfun/UltimateTerminal}"
ALIAS="ultimateterminal"
KEYSTORE="${1:-$HOME/keys/ultimateterminal-release.jks}"
mkdir -p "$(dirname "$KEYSTORE")"

for tool in keytool gh base64; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "gh is not logged in (gh auth login)" >&2; exit 1; }
[ ! -e "$KEYSTORE" ] || { echo "$KEYSTORE already exists: refusing to overwrite a key" >&2; exit 1; }

read -rsp "Keystore password (min 8 chars): " UT_STOREPASS; echo
read -rsp "Repeat it: " again; echo
[ "$UT_STOREPASS" = "$again" ] || { echo "Passwords differ" >&2; exit 1; }
[ "${#UT_STOREPASS}" -ge 8 ] || { echo "Too short" >&2; exit 1; }
# One password for the store and the key keeps the secret set simple (as in the other apps).
export UT_STOREPASS

keytool -genkeypair -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10000 -dname "CN=UltimateTerminal, O=qtekfun" \
  -storepass:env UT_STOREPASS -keypass:env UT_STOREPASS

chmod 600 "$KEYSTORE"
base64 -w0 "$KEYSTORE" | gh secret set UT_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$UT_STOREPASS" | gh secret set UT_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set UT_KEY_ALIAS --repo "$REPO"
printf '%s' "$UT_STOREPASS" | gh secret set UT_KEY_PASSWORD --repo "$REPO"

echo
echo "Secrets uploaded to $REPO. Certificate fingerprint (for AllowedAPKSigningKeys):"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env UT_STOREPASS | grep 'SHA256'
unset UT_STOREPASS again
echo
echo "BACK UP $KEYSTORE and the password now (password manager + an offline copy)."
echo "If the key is lost, users must uninstall the app to update it."
