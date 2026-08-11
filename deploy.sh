#!/bin/bash
# Generates the tool-specific artifacts for every deployment manifest: a
# project.yml is written into its own deploy.directory, a user.yml into the
# user scope of each tool it names, under the home of whoever runs this.
#
# Run it from the repository root: the current directory is passed to the engine
# as --working-dir, and that is where config.yml (and config.local.yml) are read
# from. The exit status is the engine CLI's own, so a manifest that fails to
# export fails this script too.
#
# Every argument given to this script is passed on to the engine, so
#   ./deploy.sh --user-home /tmp/try
# is how a run is tried out without touching your own configuration.

set -euo pipefail

CUR_DIR="$(pwd)"

# Gradle splits --args itself: it groups on double quotes and has no escape
# mechanism at all, so a backslash reaches the CLI as a literal character.
# Quoting each argument therefore carries a path containing spaces correctly,
# while a path containing a double quote cannot be expressed - escaping it
# would silently deliver a different, still-plausible path. Such an argument
# is refused here rather than corrupted; run the CLI directly for that case.
FORWARDED_ARGS=""
for arg in "$@"; do
    case "$arg" in
        *'"'*)
            echo "deploy.sh: cannot pass '$arg' to Gradle: --args has no way to escape a double quote." >&2
            echo "Run the engine directly instead: (cd ai-tools-engine && ./gradlew :cli:run --args=...)" >&2
            exit 2
            ;;
    esac
    FORWARDED_ARGS="$FORWARDED_ARGS \"$arg\""
done

# The subshell keeps the caller's working directory intact even when the run
# fails, and `set -e` turns a failing `cd` into an abort instead of building
# somewhere unintended. Being the last command, its status becomes the script's.
(
    cd ai-tools-engine
    # The inner quotes survive Gradle's own splitting of --args, so a repository
    # path containing spaces stays a single argument.
    ./gradlew :cli:run --args="--working-dir \"$CUR_DIR\"$FORWARDED_ARGS"
)
