#!/bin/bash

# Copyright (c) 2024 Tailscale Inc & AUTHORS All rights reserved.
# Use of this source code is governed by a BSD-style
# license that can be found in the LICENSE file.

# This script converts the generated .aab files to .apk files using bundletool.
# It handles both the standard release and the Android TV release.

set -e

# Load version info if available
if [ -f "tailscale.version" ]; then
    VERSION=$(grep "VERSION_SHORT" tailscale.version | cut -d'"' -f2)
else
    VERSION="unknown"
fi

# Keystone info (defaults from Makefile logic)
JKS_PATH="${JKS_PATH:-tailscale.jks}"
JKS_PASSWORD="${JKS_PASSWORD:-}"

if [ -z "$JKS_PASSWORD" ]; then
    echo "Error: JKS_PASSWORD environment variable is not set."
    echo "Please set it before running this script (e.g., export JKS_PASSWORD=your_password)."
    exit 1
fi

if [ ! -f "$JKS_PATH" ]; then
    echo "Error: Keystore not found at $JKS_PATH"
    exit 1
fi

convert_aab_to_apk() {
    local input_aab=$1
    local output_apk=$2
    local apks_tmp="${output_apk}.apks"

    if [ ! -f "$input_aab" ]; then
        echo "Warning: Input $input_aab not found. Skipping."
        return
    fi

    echo "Converting $input_aab to $output_apk..."

    # 1. Build APKS from AAB using local bundletool
    bundletool build-apks \
        --bundle="$input_aab" \
        --output="$apks_tmp" \
        --mode=universal \
        --ks="$JKS_PATH" \
        --ks-pass=pass:"$JKS_PASSWORD" \
        --ks-key-alias=tailscale \
        --key-pass=pass:"$JKS_PASSWORD" \
        --overwrite

    # 2. Extract the universal APK from the .apks zip
    unzip -p "$apks_tmp" universal.apk > "${output_apk}.unaligned"
    
    # 3. Align the APK to 16KB boundaries for Android 16 support
    if [ -f "./scripts/align-apk.sh" ]; then
        echo "Aligning $output_apk to 16KB boundaries..."
        ./scripts/align-apk.sh "${output_apk}.unaligned" "$output_apk"
        rm "${output_apk}.unaligned"
    else
        mv "${output_apk}.unaligned" "$output_apk"
        echo "Warning: scripts/align-apk.sh not found, skipping 16KB alignment."
    fi
    
    rm "$apks_tmp"
    echo "Successfully created: $output_apk"
}

# --- Execution ---

echo "Starting AAB to APK conversion (Version: $VERSION)..."

# Convert Standard Release
convert_aab_to_apk "tailscale-release.aab" "tailscale-release-${VERSION}.apk"

# Convert TV Release
convert_aab_to_apk "tailscale-tv-release.aab" "tailscale-tv-release-${VERSION}.apk"

echo "Done."
