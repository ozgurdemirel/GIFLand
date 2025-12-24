#!/bin/bash
# =====================================================
# Purpose: Create a customized DMG installer for macOS
# Output: composeApp/build/compose/binaries/main/*.dmg
# Requirements: Gradle Wrapper, hdiutil, osascript
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

# Build the app first
./gradlew :composeApp:createDistributable

APP_NAME="GIF Land"
APP_PATH="composeApp/build/compose/binaries/main/app/${APP_NAME}.app"
DMG_NAME="GIF-Land-1.0.0"
DMG_PATH="composeApp/build/compose/binaries/main/${DMG_NAME}.dmg"
TEMP_DMG_PATH="composeApp/build/compose/binaries/main/${DMG_NAME}-temp.dmg"
VOL_NAME="${APP_NAME}"

# Remove old DMG if exists
rm -f "${DMG_PATH}" || true
rm -f "${TEMP_DMG_PATH}" || true

# Create a temporary directory for DMG contents
TEMP_DIR=$(mktemp -d)
echo "Using temp directory: ${TEMP_DIR}"

# Copy app to temp directory
cp -R "${APP_PATH}" "${TEMP_DIR}/"

# Create Applications symlink
ln -s /Applications "${TEMP_DIR}/Applications"

# Create DMG with proper size
echo "Creating DMG..."
hdiutil create -srcfolder "${TEMP_DIR}" -volname "${VOL_NAME}" -fs HFS+ -format UDRW "${TEMP_DMG_PATH}"

# Mount the DMG
echo "Mounting DMG for customization..."
DEVICE=$(hdiutil attach -readwrite -noverify -noautoopen "${TEMP_DMG_PATH}" | egrep '^/dev/' | sed 1q | awk '{print $1}')
MOUNT_POINT="/Volumes/${VOL_NAME}"

# Wait for mount
sleep 2

# Set custom icon positions and window properties using AppleScript
echo "Customizing DMG appearance..."
osascript <<EOT
tell application "Finder"
    tell disk "${VOL_NAME}"
        open
        set current view of container window to icon view
        set toolbar visible of container window to false
        set statusbar visible of container window to false
        set the bounds of container window to {400, 100, 900, 400}
        set viewOptions to the icon view options of container window
        set arrangement of viewOptions to not arranged
        set icon size of viewOptions to 72
        set position of item "${APP_NAME}.app" of container window to {125, 150}
        set position of item "Applications" of container window to {375, 150}
        update without registering applications
        delay 2
    end tell
end tell
EOT

# Set background (optional - you can add a background image here)
# cp background.png "${MOUNT_POINT}/.background/"
# Use AppleScript to set background image

# Unmount the DMG
echo "Unmounting DMG..."
sync || true
hdiutil detach "${DEVICE}" -force

# Convert to compressed DMG
echo "Converting to final DMG..."
hdiutil convert "${TEMP_DMG_PATH}" -format UDZO -o "${DMG_PATH}"

# Clean up
rm -f "${TEMP_DMG_PATH}" || true
rm -rf "${TEMP_DIR}" || true

echo "DMG created at: ${DMG_PATH}"
open "${DMG_PATH}"