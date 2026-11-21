#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0

# Run from the repository root. Report all configurations for lock coverage,
# then run the build tasks to capture dependencies resolved during execution.
exec ./gradlew --no-daemon --no-configuration-cache --refresh-dependencies \
    --write-locks --write-verification-metadata sha256 \
    :dependencies :app:dependencies testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
