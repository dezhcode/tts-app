#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SOURCE="$HOME/avina-signing/avina-release.jks"
OUT="$HOME/avina-signing/avina-release-jks.jks"
B64="$HOME/avina-signing/avina-keystore-jks-base64.txt"
ALIAS="upload"

command -v keytool >/dev/null 2>&1 || { echo "JDK keytool is required."; exit 1; }
test -s "$SOURCE" || { echo "Original PKCS12 keystore not found: $SOURCE"; exit 1; }
test ! -e "$OUT" || { echo "Converted keystore already exists; refusing to overwrite."; exit 1; }

read -r -s -p "Original PKCS12 keystore password: " STORE_PASS; echo
read -r -s -p "Original key password: " KEY_PASS; echo

echo "Checking original key..."
keytool -list -keystore "$SOURCE" -storetype PKCS12 -storepass "$STORE_PASS" -alias "$ALIAS" >/dev/null

echo "Converting the same key to JKS (same alias and passwords)..."
keytool -importkeystore -noprompt \
  -srckeystore "$SOURCE" -srcstoretype PKCS12 -srcstorepass "$STORE_PASS" -srckeypass "$KEY_PASS" \
  -destkeystore "$OUT" -deststoretype JKS -deststorepass "$STORE_PASS" -destkeypass "$KEY_PASS" \
  -srcalias "$ALIAS" -destalias "$ALIAS"

echo "Verifying converted JKS..."
keytool -list -keystore "$OUT" -storetype JKS -storepass "$STORE_PASS" -alias "$ALIAS" >/dev/null
base64 -w 0 "$OUT" > "$B64"
chmod 600 "$OUT" "$B64"

echo
echo "SUCCESS. New JKS file: $OUT"
echo "New Base64 secret value file: $B64"
echo "Update ONLY AVINA_KEYSTORE_BASE64 in GitHub Actions secrets with the contents of that Base64 file."
echo "Keep the original PKCS12 file as an extra backup. Password secrets remain unchanged."
unset STORE_PASS KEY_PASS
