#!/usr/bin/env bash
# התקנת המודל ישירות על מכשיר עם root, לבדיקה לפני בניית קושחה.
# שימוש: ./install-model-adb.sh /path/to/system/etc/ezrim/hebrew-asr
set -euo pipefail
SRC="${1:?נתיב לתיקיית hebrew-asr (עם 7 הקבצים)}"
DEST=/system/etc/ezrim/hebrew-asr
( cd "$SRC" && sha256sum -c SHA256SUMS )
adb root >/dev/null; adb wait-for-device
# במכשירי system-as-root ייתכן שצריך קודם: adb disable-verity && adb reboot
adb shell "mount -o remount,rw / 2>/dev/null; mount -o remount,rw /system 2>/dev/null; mkdir -p $DEST"
for f in encoder.int8.onnx decoder.int8.onnx joiner.int8.onnx tokens.txt bpe.vocab model-config.json ATTRIBUTION.txt; do
  adb push "$SRC/$f" "$DEST/$f"
done
adb shell "chown -R root:root $DEST; chmod 755 $DEST; chmod 644 $DEST/*; restorecon -R $DEST 2>/dev/null || true; ls -lZ $DEST"
echo "בוצע. פתח את עזרים -> הקלדה קולית."
