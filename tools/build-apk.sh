#!/bin/sh
# Universal Android Gradle build helper.
#
# Works on AndroidHarness phones (Android/aarch64, no exec on shared storage),
# on GitHub Actions, and on desktop Linux/macOS. It contains no project-specific
# assumptions: the Gradle task is the only argument.
#
# Usage:
#   sh tools/build-apk.sh                 # builds :app:assembleDebug
#   sh tools/build-apk.sh :app:assembleRelease
#   sh tools/build-apk.sh :app:bundleRelease
#
# Optional environment overrides:
#   ANDROID_HOME      Android SDK root (auto-detected when unset)
#   GRADLE_USER_HOME  Gradle caches (auto-placed on an exec-capable filesystem)
#   GRADLE_XMX        JVM heap for the build (default 1536m)
#   GRADLE_WORKERS    Gradle workers (default 2)

set -e

TASK="${1:-:app:assembleDebug}"
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_DIR"

say() { printf '\n[build-apk] %s\n' "$*"; }

# ---------------------------------------------------------------------------
# 1. Android SDK
# ---------------------------------------------------------------------------
if [ -z "$ANDROID_HOME" ]; then
  for candidate in "$HOME/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/local/lib/android/sdk"; do
    if [ -d "$candidate" ]; then
      ANDROID_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "$ANDROID_HOME" ]; then
  echo "[build-apk] ERROR: no Android SDK found. Set ANDROID_HOME." >&2
  exit 1
fi
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
# local.properties is machine specific and is expected to be git-ignored.
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
say "ANDROID_HOME=$ANDROID_HOME"

# ---------------------------------------------------------------------------
# 2. JDK (Gradle needs one; some environments do not export JAVA_HOME)
# ---------------------------------------------------------------------------
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  for jdk_dir in "$HOME"/../lib/jvm/* /usr/lib/jvm/* /opt/java/*; do
    if [ -x "$jdk_dir/bin/java" ]; then
      JAVA_HOME="$jdk_dir"
      break
    fi
  done
  if [ -n "$JAVA_HOME" ]; then
    export JAVA_HOME
  fi
fi
say "JAVA_HOME=${JAVA_HOME:-<from PATH>}"

# ---------------------------------------------------------------------------
# 3. Gradle user home on a filesystem that allows executing native code.
#    On Android, shared storage is mounted noexec, so Gradle cannot extract and
#    load libnative-platform.so there. The harness scratch dir is exec-capable.
# ---------------------------------------------------------------------------
if [ -z "$GRADLE_USER_HOME" ]; then
  if [ -n "$HARNESS_SCRATCH" ]; then
    GRADLE_USER_HOME="$HARNESS_SCRATCH/gradle-home"
  else
    GRADLE_USER_HOME="$HOME/.gradle"
  fi
fi
mkdir -p "$GRADLE_USER_HOME"
export GRADLE_USER_HOME
say "GRADLE_USER_HOME=$GRADLE_USER_HOME"

# ---------------------------------------------------------------------------
# 3. aapt2 that can really run on this host.
#    Android binaries in the harness must be started through /system/bin/linker64,
#    and AGP launches aapt2 as a child process, so we hand AGP a tiny wrapper.
# ---------------------------------------------------------------------------
find_in_path() {
  _n="$1"
  _old_ifs=$IFS
  IFS=:
  for _d in $PATH; do
    if [ -x "$_d/$_n" ]; then
      IFS=$_old_ifs
      printf '%s\n' "$_d/$_n"
      return 0
    fi
  done
  IFS=$_old_ifs
  return 1
}

AAPT2_OVERRIDE=""
aapt2_bin=$(find_in_path aapt2 || true)
if [ -n "$aapt2_bin" ]; then
  if "$aapt2_bin" version >/dev/null 2>&1; then
    AAPT2_OVERRIDE="$aapt2_bin"
  elif [ -x /system/bin/linker64 ]; then
    mkdir -p "$GRADLE_USER_HOME/bin"
    AAPT2_OVERRIDE="$GRADLE_USER_HOME/bin/aapt2"
    {
      printf '#!/system/bin/sh\n'
      printf 'exec /system/bin/linker64 %s "$@"\n' "$aapt2_bin"
    } > "$AAPT2_OVERRIDE"
    chmod 755 "$AAPT2_OVERRIDE"
  fi
fi
if [ -n "$AAPT2_OVERRIDE" ]; then
  say "aapt2 override=$AAPT2_OVERRIDE"
else
  say "aapt2: using the plugin's own binary"
fi

# ---------------------------------------------------------------------------
# 4. Build
# ---------------------------------------------------------------------------
# Prefer the project wrapper: it pins the Gradle version the Android plugin
# expects. It is started through an explicit shell because shared storage on
# Android is mounted noexec and the host PATH may not contain `sh`.
SH_BIN=""
for candidate_sh in /system/bin/sh /bin/sh; do
  if [ -x "$candidate_sh" ]; then
    SH_BIN="$candidate_sh"
    break
  fi
done
if [ -z "$SH_BIN" ]; then
  SH_BIN=$(command -v sh 2>/dev/null || true)
fi

if [ -f ./gradlew ]; then
  if [ -z "$SH_BIN" ]; then
    echo "[build-apk] ERROR: no POSIX shell found to run ./gradlew" >&2
    exit 1
  fi
  cp ./gradlew "$GRADLE_USER_HOME/bin/gradlew"
  chmod 755 "$GRADLE_USER_HOME/bin/gradlew"
  cp -r gradle/wrapper "$GRADLE_USER_HOME/bin/wrapper" 2>/dev/null || true
  GRADLE_CMD="$SH_BIN $GRADLE_USER_HOME/bin/gradlew"
else
  GRADLE_CMD=$(find_in_path gradle || true)
  if [ -z "$GRADLE_CMD" ]; then
    echo "[build-apk] ERROR: neither ./gradlew nor gradle is available." >&2
    exit 1
  fi
  # Android binaries may need /system/bin/linker64 to start.
  if ! "$GRADLE_CMD" --version >/dev/null 2>&1; then
    if [ -x /system/bin/linker64 ]; then
      mkdir -p "$GRADLE_USER_HOME/bin"
      GRADLE_WRAPPER="$GRADLE_USER_HOME/bin/gradle"
      {
        printf '#!/system/bin/sh\n'
        printf 'exec /system/bin/linker64 %s "$@"\n' "$GRADLE_CMD"
      } > "$GRADLE_WRAPPER"
      chmod 755 "$GRADLE_WRAPPER"
      GRADLE_CMD="$GRADLE_WRAPPER"
    fi
  fi
fi

# Make the Android platform tools reachable by child processes.
if [ -d /system/bin ] && ! echo ":$PATH:" | grep -q ":/system/bin:"; then
  PATH="/system/bin:$PATH"
  export PATH
fi

XMX="${GRADLE_XMX:-1536m}"
WORKERS="${GRADLE_WORKERS:-2}"

run_gradle() {
  if [ -n "$AAPT2_OVERRIDE" ]; then
    set -- -Pandroid.aapt2FromMavenOverride="$AAPT2_OVERRIDE" "$@"
  fi
  cd "$PROJECT_DIR"
  $GRADLE_CMD "$TASK" "$@" \
    --no-daemon \
    "-Dorg.gradle.jvmargs=-Xmx$XMX -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" \
    "-Dorg.gradle.workers.max=$WORKERS" \
    -Dorg.gradle.vfs.watch=false \
    -Dkotlin.compiler.execution.strategy=in-process
}

say "running: $GRADLE_CMD $TASK"
LOG_FILE="$PROJECT_DIR/build-apk.log"
STATUS_FILE="$PROJECT_DIR/build-apk.status"
rm -f "$STATUS_FILE"
set +e
# The braces run in a subshell (pipeline), so $? there is Gradle's own exit code.
{ run_gradle "" 2>&1; echo $? > "$STATUS_FILE"; } | tee "$LOG_FILE"
STATUS=$(cat "$STATUS_FILE" 2>/dev/null || echo 1)
set -e

# Gradle's native services fail on filesystems without exec permission; if that
# is the only problem, retry once with the native integration disabled.
if [ "$STATUS" -ne 0 ] && grep -q "Failed to load native library" "$LOG_FILE"; then
  say "retrying with -Dorg.gradle.native=false"
  rm -f "$STATUS_FILE"
  set +e
  { run_gradle "-Dorg.gradle.native=false" 2>&1; echo $? > "$STATUS_FILE"; } | tee "$LOG_FILE"
  STATUS=$(cat "$STATUS_FILE" 2>/dev/null || echo 1)
  set -e
fi

if [ "$STATUS" -ne 0 ]; then
  echo "[build-apk] BUILD FAILED (exit $STATUS). Last lines:" >&2
  tail -40 "$LOG_FILE" >&2
  exit "$STATUS"
fi

say "BUILD OK"
APK=$(find "$PROJECT_DIR" -path '*/build/outputs/apk/*' -name '*.apk' -type f 2>/dev/null | head -1)
if [ -n "$APK" ]; then
  say "APK: $APK"
  ls -la "$APK"
else
  say "No APK found under build/outputs/apk"
fi
