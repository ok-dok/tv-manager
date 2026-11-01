#!/bin/bash
# 电视管家 (TV Manager) - build APK for Android TV boxes
# deps: javac (JDK 11+), d8, aapt, apksigner, workspace needs Android SDK build-tools/36.1.0 (or edit BT)
set -e
cd "$(dirname "$0")"

BT="${ANDROID_BUILD_TOOLS:-$HOME/Library/Android/sdk/build-tools/36.1.0}"
PLAT="${ANDROID_PLATFORM:-$HOME/Library/Android/sdk/platforms/android-36/android.jar}"

rm -rf obj out && mkdir -p obj out
echo ">>> javac"
javac -source 8 -target 8 -classpath "$PLAT" -d obj src/com/dokey/homecontrol/*.java
echo ">>> d8"
$BT/d8 --release --min-api 21 --output out/ obj/com/dokey/homecontrol/*.class
echo ">>> aapt"
$BT/aapt package -f -M AndroidManifest.xml -S res -I "$PLAT" -F out/base.apk
echo ">>> zip"
(cd out && zip -q base.apk classes.dex)
echo ">>> apksigner"
(cd out && $BT/apksigner sign \
  --ks "${KEYSTORE:-$HOME/.android/debug.keystore}" \
  --ks-pass pass:android \
  --out homecontrol-signed.apk base.apk)

echo ">>> done: $(argv0)" 2>/dev/null || true
ls -la out/homecontrol-signed.apk
