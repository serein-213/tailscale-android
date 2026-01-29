#!/bin/bash
set -e

INPUT_APK=$1
OUTPUT_APK=$2

if [ -z "$INPUT_APK" ] || [ -z "$OUTPUT_APK" ]; then
    echo "Usage: $0 <input.apk> <output.apk>"
    exit 1
fi

# Find zipalign
ZIPALIGN=$(find $ANDROID_HOME/build-tools -name zipalign | sort -V | tail -n 1)

if [ -z "$ZIPALIGN" ]; then
    echo "Error: zipalign not found in $ANDROID_HOME/build-tools"
    exit 1
fi

echo "Using zipalign at: $ZIPALIGN"
echo "Aligning $INPUT_APK to $OUTPUT_APK with 16KB boundary..."

$ZIPALIGN -p -f -v 16 "$INPUT_APK" "$OUTPUT_APK"

echo "Verification:"
$ZIPALIGN -c -v 16 "$OUTPUT_APK" | grep -i "verification" || true
$ZIPALIGN -c -v 16 "$OUTPUT_APK" > /dev/null && echo "Alignment Successful (16KB)" || echo "Alignment Failed"
