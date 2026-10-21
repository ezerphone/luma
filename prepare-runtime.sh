#!/usr/bin/env bash
# מוריד את ספריית sherpa-onnx הרשמית ל-app/libs ומאמת SHA256. להריץ לפני הבנייה.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)/app/libs"
FILE="$DIR/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"
HASH="b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"
mkdir -p "$DIR"
if [ -f "$FILE" ]; then
  [ "$(sha256sum "$FILE" | cut -d' ' -f1)" = "$HASH" ] || { echo "Existing runtime hash differs. Review it before replacing the file." >&2; exit 1; }
  echo "Verified runtime is already present."; exit 0
fi
curl -fL --retry 3 -o "$FILE.download" "$URL"
[ "$(sha256sum "$FILE.download" | cut -d' ' -f1)" = "$HASH" ] || { rm -f "$FILE.download"; echo "Runtime download did not match the official SHA256. Nothing installed." >&2; exit 1; }
mv "$FILE.download" "$FILE"
echo "Official runtime downloaded and verified."
