#!/bin/bash
# Binti2 APK Build Script
# Run this script to build the debug APK

cd /home/z/my-project/binti2

echo "🚗 Building Binti2 Debug APK..."
echo "=============================="

# Clean and build
./gradlew clean assembleDebug --no-daemon

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo "=============================="
    
    # Find the APK
    APK_PATH=$(find app/build/outputs -name "*.apk" -type f 2>/dev/null | head -1)
    
    if [ -n "$APK_PATH" ]; then
        # Copy to project root
        cp "$APK_PATH" ./app-debug.apk
        
        SIZE=$(du -h ./app-debug.apk | cut -f1)
        echo "📦 APK Location: $(pwd)/app-debug.apk"
        echo "📏 APK Size: $SIZE"
        echo ""
        echo "To install on device:"
        echo "  adb install -r ./app-debug.apk"
    fi
else
    echo ""
    echo "❌ Build failed!"
    echo "Check the error messages above."
    exit 1
fi
