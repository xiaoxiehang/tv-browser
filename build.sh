#!/bin/bash
# 鸡仔浏览器 TV App 手动构建脚本（零 Gradle 依赖）
# 流水线：aapt2 编译资源 -> aapt2 链接 -> kotlinc 编译 -> d8 转 dex -> zipalign -> apksigner
set -e

PROJ="$HOME/workspace/tv-browser"
SRC="$PROJ/app/src/main"
OUT=/tmp/tvbuild
BT=/opt/android-sdk/build-tools/34.0.0
PLATFORM=/opt/android-sdk/platforms/android-34/android.jar
DEPS=/tmp/tvdeps
KOTLINC=/opt/kotlinc/kotlinc/bin/kotlinc
export JAVA_HOME=/opt/jdk/jdk-17.0.11+9
export PATH="$JAVA_HOME/bin:$PATH"

WS_JAR="$DEPS/Java-WebSocket-1.5.6.jar"
SLF4J_JAR="$DEPS/slf4j-api-1.7.36.jar"
ZXING_JAR="$DEPS/core-3.5.3.jar"
STDLIB_JAR="/opt/kotlinc/kotlinc/lib/kotlin-stdlib.jar"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$KOTLINC" "$PLATFORM" "$WS_JAR" "$SLF4J_JAR" "$ZXING_JAR" "$STDLIB_JAR"; do
  [ -e "$f" ] || { echo "MISSING: $f"; exit 1; }
done

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> [1/6] aapt2 compile resources"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/compiled.zip"

echo "==> [2/6] aapt2 link"
"$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$SRC/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  "$OUT/compiled.zip"

echo "==> [3/6] kotlinc"
# shellcheck disable=SC2046
if ! "$KOTLINC" $(find "$SRC/java" "$OUT/gen" \( -name "*.kt" -o -name "*.java" \)) \
  -cp "$PLATFORM:$WS_JAR:$SLF4J_JAR:$ZXING_JAR" \
  -d "$OUT/classes" -jvm-target 17 > "$OUT/kotlinc.log" 2>&1; then
  grep -E "error:" "$OUT/kotlinc.log" | head -20
  exit 1
fi
grep -c "^warning:" "$OUT/kotlinc.log" | xargs -I{} echo "kotlinc warnings: {}"

echo "==> [4/6] d8 -> dex"
# shellcheck disable=SC2046
"$BT/d8" --lib "$PLATFORM" --min-api 24 --output "$OUT/dex" \
  $(find "$OUT/classes" -name "*.class") \
  "$WS_JAR" "$SLF4J_JAR" "$ZXING_JAR" "$STDLIB_JAR"

echo "==> [5/6] add dex + zipalign"
cp "$OUT/base.apk" "$OUT/app-unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/app-unsigned.apk" classes*.dex)
"$BT/zipalign" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "==> [6/6] sign"
if [ ! -f "$HOME/.tvbrowser-debug.keystore" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$HOME/.tvbrowser-debug.keystore" \
    -alias tv -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass android -keypass android -dname "CN=JizaiTV" > /dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$HOME/.tvbrowser-debug.keystore" \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/app-debug.apk" "$OUT/app-aligned.apk"

"$BT/apksigner" verify "$OUT/app-debug.apk" && echo "VERIFY OK"
ls -la "$OUT/app-debug.apk"
