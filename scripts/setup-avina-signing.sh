#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

REPO="dezhcode/tts-app"
ALIAS="upload"
OUT_DIR="$HOME/avina-signing"
KEYSTORE="$OUT_DIR/avina-release.jks"

command -v keytool >/dev/null 2>&1 || { echo "JDK keytool is missing. Run: sudo apt-get update && sudo apt-get install -y openjdk-17-jdk-headless"; exit 1; }
command -v gh >/dev/null 2>&1 || { echo "GitHub CLI is missing."; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Authenticate first with: gh auth login"; exit 1; }

mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"

if [[ -e "$KEYSTORE" ]]; then
  echo "Keystore already exists at $KEYSTORE; refusing to overwrite it."
  exit 1
fi

echo "Create a strong password for the Avina keystore."
read -r -s -p "Keystore password (min 6 chars): " STORE_PASS; echo
read -r -s -p "Key password (min 6 chars): " KEY_PASS; echo
[[ ${#STORE_PASS} -ge 6 && ${#KEY_PASS} -ge 6 ]] || { echo "Passwords must contain at least 6 characters."; exit 1; }

echo "Generating permanent Avina release key..."
keytool -genkeypair -noprompt -v \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Avina, OU=Mobile, O=Avina, C=US"

echo "Verifying generated keystore..."
keytool -list -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$STORE_PASS" -alias "$ALIAS" >/dev/null

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"; unset STORE_PASS KEY_PASS' EXIT
base64 -w 0 "$KEYSTORE" > "$TMP/keystore.b64"
printf '%s' "$STORE_PASS" > "$TMP/store.pass"
printf '%s' "$KEY_PASS" > "$TMP/key.pass"
chmod 600 "$TMP"/*

echo "Uploading encrypted GitHub Actions secrets to $REPO..."
gh secret set AVINA_KEYSTORE_BASE64 --repo "$REPO" < "$TMP/keystore.b64"
gh secret set AVINA_STORE_PASSWORD --repo "$REPO" < "$TMP/store.pass"
gh secret set AVINA_KEY_PASSWORD --repo "$REPO" < "$TMP/key.pass"

echo
echo "SUCCESS: all three GitHub Actions secrets were uploaded."
echo "IMPORTANT: download and securely back up this private keystore:"
echo "$KEYSTORE"
echo "Never commit it to Git. Keep both passwords in a password manager."
