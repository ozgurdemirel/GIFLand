#!/bin/bash
# =====================================================
# Purpose: Build and package the app with all native components
# Output: composeApp/build/compose/binaries/main/dmg/*.dmg
# Requirements: Gradle Wrapper, JDK 17+
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🔨 Building complete package..."

# JAVE2 provides signed FFmpeg binaries - no need to build or bundle
echo "✅ Using JAVE2 with signed FFmpeg binaries"
echo "✅ No FFmpeg building or bundling required"

# Clean and build the application
echo "🧹 Cleaning previous builds..."
./gradlew clean

echo "📦 Building DMG package..."
./gradlew :composeApp:packageDmg

# Check DMG size and copy with canonical name
DMG_DIR="composeApp/build/compose/binaries/main/dmg"
DMG_PATH=$(find "$DMG_DIR" -name "*.dmg" -type f 2>/dev/null | head -n 1 || true)
if [ -n "$DMG_PATH" ] && [ -f "$DMG_PATH" ]; then
    DMG_SIZE=$(ls -lh "$DMG_PATH" | awk '{print $5}')

    # Produce a clearly named artifact for CI
    TARGET_DMG="$DMG_DIR/gif-land-mac-silicon.dmg"
    cp "$DMG_PATH" "$TARGET_DMG"

    echo ""
    echo "✅ Build complete!"
    echo "📦 DMG created: $TARGET_DMG"
    echo "📏 DMG size: $DMG_SIZE"
    echo ""
    echo "🎯 Package includes:"
    echo "   - Kotlin/Compose Desktop application"
    echo "   - JAVE2 with signed FFmpeg binaries"
    echo "   - No external FFmpeg dependencies required"
else
    echo "❌ DMG build failed!"
    exit 1
fi