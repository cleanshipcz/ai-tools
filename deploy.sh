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
# Pass --user-home to the engine to deploy the user scope somewhere else, which
# is how a run is tried out without touching your own configuration.

set -euo pipefail

CUR_DIR="$(pwd)"

# The subshell keeps the caller's working directory intact even when the run
# fails, and `set -e` turns a failing `cd` into an abort instead of building
# somewhere unintended. Being the last command, its status becomes the script's.
(
    cd ai-tools-engine
    # The inner quotes survive Gradle's own splitting of --args, so a repository
    # path containing spaces stays a single argument.
    ./gradlew :cli:run --args="--working-dir \"$CUR_DIR\""
)
