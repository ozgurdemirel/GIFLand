#!/bin/bash
# =====================================================
# Purpose: Test the recording functionality
# Output: Test recording in ~/Documents
# Requirements: Application must be built first
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

echo "🧪 Testing GIF Land..."
echo ""
echo "This script will:"
echo "  1. Build the application"
echo "  2. Run it in test mode"
echo "  3. Check for output files in ~/Documents"
echo ""

# Build first
echo "📦 Building application..."
./gradlew :composeApp:createDistributable

# Run the app
echo "🚀 Starting application..."
echo "Please test recording manually and press Ctrl+C when done."
./gradlew :composeApp:run

# Check for recordings
echo ""
echo "🔍 Looking for recordings in ~/Documents..."
ls -la ~/Documents/recording_* 2>/dev/null || echo "No recordings found yet."