#!/usr/bin/env bash
# First-run setup for the ai-tools repository.
#
# Verifies prerequisites, builds the Kotlin engine in ai-tools-engine/, and
# points you at the real next command. It does not deploy anything - run
# ./deploy.sh when you are ready to generate tool configs.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENGINE_DIR="$REPO_ROOT/ai-tools-engine"

echo "AI Tools - Setup"
echo "================"
echo ""

# ---------------------------------------------------------------------------
# 1. Prerequisites
# ---------------------------------------------------------------------------
echo "Checking prerequisites..."

if ! command -v java &> /dev/null; then
    echo "  FAIL: 'java' not found."
    echo "        The engine is built with Gradle 9, which needs a JDK 17 or newer on PATH."
    exit 1
fi

# "java -version" writes to stderr; the first line looks like:
#   openjdk version "21.0.2" 2024-01-16
JAVA_VERSION_RAW="$(java -version 2>&1 | head -n 1 | sed -E 's/.*version "([^"]+)".*/\1/')"
JAVA_MAJOR="$(echo "$JAVA_VERSION_RAW" | cut -d. -f1)"
if [ "$JAVA_MAJOR" = "1" ]; then
    # Pre-9 scheme, e.g. 1.8.0_392
    JAVA_MAJOR="$(echo "$JAVA_VERSION_RAW" | cut -d. -f2)"
fi

if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ] 2>/dev/null; then
    echo "  FAIL: Java $JAVA_VERSION_RAW found, but Gradle 9 requires JDK 17 or newer."
    exit 1
fi
echo "  OK: Java $JAVA_VERSION_RAW"

if [ ! -x "$ENGINE_DIR/gradlew" ]; then
    echo "  FAIL: $ENGINE_DIR/gradlew not found or not executable."
    echo "        Are you running this from a complete checkout?"
    exit 1
fi
echo "  OK: Gradle wrapper at ai-tools-engine/gradlew"

if [ ! -f "$REPO_ROOT/config.yml" ]; then
    echo "  FAIL: $REPO_ROOT/config.yml not found."
    echo "        The engine reads config.yml from the repository root."
    exit 1
fi
echo "  OK: config.yml"

# The build targets JVM 21. If no JDK 21 is installed locally, Gradle's toolchain
# resolver downloads one on the first build, which needs network access.
echo "  NOTE: the engine targets JVM 21. If you have no local JDK 21, the first"
echo "        build downloads one automatically (needs network access)."
echo ""

# ---------------------------------------------------------------------------
# 2. Build the engine
# ---------------------------------------------------------------------------
# A global Gradle init script that registers repositories via allprojects {} is
# rejected by this build, because settings.gradle.kts sets
# RepositoriesMode.FAIL_ON_PROJECT_REPOS. We do not touch the user's Gradle home
# unless that specific failure actually occurs, and we always restore it.
MOVED_INIT_SCRIPTS=()

restore_init_scripts() {
    local script
    for script in "${MOVED_INIT_SCRIPTS[@]:-}"; do
        [ -n "$script" ] || continue
        if [ -f "$script.ai-tools-setup-bak" ]; then
            mv "$script.ai-tools-setup-bak" "$script"
            echo "  Restored $script"
        fi
    done
}
BUILD_LOG="$(mktemp)"

cleanup() {
    rm -f "$BUILD_LOG"
    restore_init_scripts
}

# A trap handler returns to where the script was interrupted, so a plain handler
# would let Ctrl-C during the first build fall straight into the second one. This
# one therefore stops the script itself, after restoring everything it moved.
abort_on_signal() {
    local signal="$1"
    echo ""
    echo "Interrupted by SIG$signal - cleaning up."
    trap - EXIT "$signal"
    cleanup
    kill -s "$signal" "$$"
    # bash ignores SIGQUIT outright, so re-raising it does not stop the script.
    exit "$((128 + $(kill -l "$signal")))"
}

trap cleanup EXIT
# HUP covers a closed terminal, QUIT covers Ctrl-\, both of which would otherwise
# leave the user's Gradle home with the init script still moved aside.
for signal in INT TERM HUP QUIT; do
    trap "abort_on_signal $signal" "$signal"
done

run_build() {
    (cd "$ENGINE_DIR" && ./gradlew :cli:build) 2>&1 | tee "$BUILD_LOG"
    return "${PIPESTATUS[0]}"
}

# :cli:build covers everything ./deploy.sh needs (:cli, :engine, :telemetry)
# and runs their tests, ktlint and detekt. The full `./gradlew build` also builds
# :server, whose frontend shells out to npm, so it additionally requires Node.
echo "Building the engine (./gradlew :cli:build in ai-tools-engine/)..."
echo ""

if ! run_build; then
    # Gradle names the offending file: "... was added by initialization script '<path>'"
    mapfile -t OFFENDING < <(grep -oE "added by initialization script '[^']+'" "$BUILD_LOG" \
        | sed -E "s/.*'([^']+)'.*/\1/" | sort -u)

    if [ "${#OFFENDING[@]}" -eq 0 ]; then
        echo ""
        echo "Build failed. See the Gradle output above."
        exit 1
    fi

    echo ""
    echo "-----------------------------------------------------------------------"
    echo "Build failed because a global Gradle init script registers repositories,"
    echo "which this build rejects (settings.gradle.kts uses FAIL_ON_PROJECT_REPOS)."
    echo ""
    echo "Retrying with the following moved aside TEMPORARILY:"
    for script in "${OFFENDING[@]}"; do
        echo "  - $script"
    done
    echo ""
    echo "They are restored automatically when this script exits, including on"
    echo "Ctrl-C. If setup is killed outright, restore them yourself with:"
    for script in "${OFFENDING[@]}"; do
        echo "  mv '$script.ai-tools-setup-bak' '$script'"
    done
    echo "-----------------------------------------------------------------------"
    echo ""

    for script in "${OFFENDING[@]}"; do
        if [ -e "$script.ai-tools-setup-bak" ]; then
            # Overwriting this would destroy the init script a previous run backed
            # up but never restored, so stop and let the user sort it out.
            echo "  FAIL: $script.ai-tools-setup-bak already exists."
            echo "        A previous setup run was killed before it could restore it."
            echo "        Check both files and put the backup back yourself, then re-run setup:"
            echo "          mv '$script.ai-tools-setup-bak' '$script'"
            exit 1
        fi
        if [ -f "$script" ]; then
            mv "$script" "$script.ai-tools-setup-bak"
            MOVED_INIT_SCRIPTS+=("$script")
        fi
    done

    if ! run_build; then
        echo ""
        echo "Build still failed. See the Gradle output above."
        exit 1
    fi
fi

echo ""
echo "Build succeeded."
echo ""

# ---------------------------------------------------------------------------
# 3. Next steps
# ---------------------------------------------------------------------------
cat <<'EOF'
Setup complete.

What just happened:
  - The Kotlin CLI and engine in ai-tools-engine/ were compiled and their
    checks (tests, ktlint, detekt) passed.
  - Nothing was generated or deployed yet.

The optional :server module was not built. It bundles a Vite frontend and
needs Node/npm; build it with `cd ai-tools-engine && ./gradlew build`.

Next step - generate and deploy tool configs:
  ./deploy.sh

That runs the engine over every project manifest found under the `projects`
locations in config.yml, and writes tool-specific files (.claude/, .github/,
.windsurf/, .cursor/, .codex/, .agent/, CLAUDE.md, AGENTS.md) into each
project's `deploy.directory`.

To change which manifests and tools are used, edit config.yml, or create
config.local.yml next to it for machine-local overrides (gitignored).

Documentation:
  README.md          - overview, layout, and workflow
  QUICKREF.md        - manifest templates and field reference
  90_docs/TOOLS.md   - per-tool integration details
  PLANNED_FEATURES.md - capabilities that are not implemented yet
EOF
