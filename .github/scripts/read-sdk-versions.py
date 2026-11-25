#!/usr/bin/env python3
# SPDX-License-Identifier: MPL-2.0
"""Prints the Android SDK settings the workflows install and assert, read from the version catalog."""

import pathlib
import sys
import tomllib

CATALOG = pathlib.Path(__file__).resolve().parents[2] / "gradle" / "libs.versions.toml"


def version(versions: dict[str, str], key: str) -> str:
    if key not in versions:
        sys.exit(f"Missing {key} in {CATALOG}")
    return versions[key]


versions = tomllib.loads(CATALOG.read_text())["versions"]
settings = {
    "SDK_PLATFORM": f"platforms;android-{version(versions, 'android-platform')}",
    "BUILD_TOOLS_VERSION": version(versions, "android-buildTools"),
    "MIN_SDK": version(versions, "android-minSdk"),
    "TARGET_SDK": version(versions, "android-targetSdk"),
}
# Read every value before printing, so a missing key cannot produce a partial environment.
for key, value in settings.items():
    print(f"{key}={value}")
