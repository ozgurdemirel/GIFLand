#!/bin/bash
# =====================================================
# Purpose: Run the app in development mode with optimized settings
# Output: Runs Compose app with hot reload (if configured)
# Requirements: JDK 17+, Gradle Wrapper
# =====================================================

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(cd "$script_dir/../.." && pwd -P)"
pushd "$repo_root" >/dev/null
cleanup() { popd >/dev/null || true; }
trap cleanup EXIT

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
CLEAN_BUILD=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            CLEAN_BUILD=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --clean        Clean build (removes build directories)"
            echo "  --help         Show this help message"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# JAVE2 provides signed FFmpeg binaries - no manual building needed
echo -e "${GREEN}✅ Using JAVE2 with signed FFmpeg binaries${NC}"

# Clean if requested
if [ "$CLEAN_BUILD" = true ]; then
    echo -e "${YELLOW}🧹 Cleaning build directories...${NC}"
    ./gradlew clean
    rm -rf composeApp/build
    rm -rf build
    rm -rf .gradle/caches
    echo -e "${GREEN}✅ Clean completed${NC}"
fi

echo -e "${GREEN}🚀 Starting GIF Land in development mode...${NC}"
echo -e "${YELLOW}📋 Build Configuration:${NC}"
echo "   • Clean Build: $CLEAN_BUILD"
echo "   • FFmpeg: JAVE2 integrated (signed binaries)"
echo "   • Memory: -Xmx2G -Xms256M"
echo ""

# Run the Compose Desktop application
./gradlew :composeApp:run \
    -Dorg.gradle.jvmargs="-Xmx2G -Xms256M -XX:+UseG1GC" \
    -Dkotlin.daemon.jvmargs="-Xmx2G" \
    --parallel \
    --console=plain \
    --no-configuration-cache \
    $(if [ "$CLEAN_BUILD" = true ]; then echo "--rerun-tasks"; fi)

# Alternative for running distributable:
# ./gradlew :composeApp:runDistributable --no-daemon