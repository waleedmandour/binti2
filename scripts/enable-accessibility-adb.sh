#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────
# Binti Accessibility Enabler — ADB workaround for BYD DiLink 3
#
# BYD DiLink 3 (Android 10) has an OEM-modified settings app that often
# prevents accessibility services from being enabled through the UI.
# This script enables Binti's accessibility service directly via ADB,
# bypassing the broken Settings → Accessibility page.
#
# Prerequisites:
#   1. ADB enabled on the DiLink head unit (Settings → About → tap
#      "Software version" 7 times to enable Developer Options, then
#      enable "USB debugging" in Developer Options)
#   2. USB cable connected from your laptop to the DiLink USB port
#   3. adb installed on your laptop (Android Platform Tools)
#
# Usage:
#   chmod +x enable-accessibility-adb.sh
#   ./enable-accessibility-adb.sh
#
# After running this script:
#   - The accessibility service will be enabled immediately
#   - Open the Binti app → "Vehicle State" → verify "Instance connected: true"
#   - You may need to re-run this script after a head unit reboot
#     (DiLink's task killer can disable accessibility on boot)
#
# SPDX-License-Identifier: Apache-2.0
# Copyright 2024-2025 Dr. Waleed Mandour
# ──────────────────────────────────────────────────────────────────────────

set -e

echo "═══════════════════════════════════════════════════════════════"
echo "  Binti Accessibility Enabler — ADB workaround for DiLink 3"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "❌ ERROR: adb is not installed or not in PATH."
    echo "   Install Android Platform Tools:"
    echo "   https://developer.android.com/tools/releases/platform-tools"
    exit 1
fi

# Check for connected device
echo "📋 Checking for connected DiLink head unit..."
DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    echo "❌ ERROR: No ADB device found."
    echo ""
    echo "   1. Enable USB debugging on the DiLink head unit:"
    echo "      Settings → About → tap 'Software version' 7 times"
    echo "      → Developer Options → enable 'USB debugging'"
    echo "   2. Connect a USB cable from your laptop to the DiLink USB port"
    echo "   3. If prompted on the head unit, tap 'Allow USB debugging'"
    exit 1
fi

echo "✅ Found $DEVICES device(s):"
adb devices
echo ""

# Detect the Binti package name (store or enthusiast, debug or release)
echo "🔍 Detecting installed Binti package..."
BINTI_PKG=""
for pkg in \
    "com.binti.dilink" \
    "com.binti.dilink.enthusiast" \
    "com.binti.dilink.debug" \
    "com.binti.dilink.enthusiast.debug"; do
    if adb shell pm list packages | grep -q "$pkg"; then
        BINTI_PKG="$pkg"
        break
    fi
done

if [ -z "$BINTI_PKG" ]; then
    echo "❌ ERROR: No Binti package found on the device."
    echo "   Install Binti first: adb install -r binti-*.apk"
    exit 1
fi

echo "✅ Found Binti package: $BINTI_PKG"
echo ""

# The accessibility service canonical name
SERVICE_NAME="$BINTI_PKG/com.binti.dilink.dilink.DiLinkAccessibilityService"

echo "🔧 Enabling accessibility service via ADB..."
echo "   Service: $SERVICE_NAME"
echo ""

# Read the current list of enabled accessibility services (if any)
CURRENT=$(adb shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')

if [ -z "$CURRENT" ] || [ "$CURRENT" = "null" ]; then
    # No services currently enabled — set just Binti
    NEW_VALUE="$SERVICE_NAME"
else
    # Append Binti to the existing list (colon-separated)
    # First check if it's already in the list
    if echo "$CURRENT" | grep -q "$SERVICE_NAME"; then
        echo "ℹ️  Service already in the enabled list. Ensuring it's active..."
        NEW_VALUE="$CURRENT"
    else
        NEW_VALUE="$CURRENT:$SERVICE_NAME"
    fi
fi

# Write the settings
adb shell settings put secure enabled_accessibility_services "$NEW_VALUE"
echo "✅ Set enabled_accessibility_services"

# Also enable the global accessibility toggle
adb shell settings put secure accessibility_enabled 1
echo "✅ Set accessibility_enabled=1"

# Verify
echo ""
echo "📋 Verification:"
ENABLED=$(adb shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
GLOBAL=$(adb shell settings get secure accessibility_enabled 2>/dev/null | tr -d '\r')
echo "   enabled_accessibility_services: $ENABLED"
echo "   accessibility_enabled: $GLOBAL"

if echo "$ENABLED" | grep -q "$SERVICE_NAME"; then
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo "  ✅ SUCCESS! Accessibility service enabled."
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "  Next steps:"
    echo "  1. Open the Binti app on the DiLink screen"
    echo "  2. Tap 'Vehicle State' (حالة العربية)"
    echo "  3. Scroll to 'Accessibility Diagnostics'"
    echo "  4. Verify 'Instance connected: true'"
    echo ""
    echo "  ⚠️  Note: DiLink's task killer may disable accessibility on"
    echo "     reboot. If the service stops working after a reboot, re-run"
    echo "     this script."
    echo ""
    echo "  To make it survive reboots, you can set up a Tasker profile"
    echo "  or use the 'BootReceiver' in Binti to re-trigger enablement."
    echo "═══════════════════════════════════════════════════════════════"
else
    echo ""
    echo "❌ FAILED: Service not found in enabled list after write."
    echo "   The DiLink head unit may be actively blocking the setting."
    echo "   Try the following alternatives:"
    echo "   1. Reboot the head unit and try again"
    echo "   2. Try: adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS"
    echo "      and manually toggle the service in the Settings UI"
    echo "   3. If the Settings page is missing, try the 'Direct Control' mode"
    echo "      (coming in v2.4.0 — uses app_process reflection instead of"
    echo "      accessibility)"
fi
