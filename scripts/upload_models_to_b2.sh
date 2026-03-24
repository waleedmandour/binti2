#!/bin/bash
#
# Binti2 Model Upload Script for Backblaze B2
# 
# Usage:
#   export B2_BUCKET=binti2-models
#   ./scripts/upload_models_to_b2.sh
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║          Binti2 Model Upload Script for Backblaze B2       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"

# Configuration
BUCKET_NAME="${B2_BUCKET:-binti2-models}"
MODELS_DIR="${MODELS_DIR:-./models_to_upload}"

# Check B2 CLI
if ! command -v b2 &> /dev/null; then
    echo -e "${RED}❌ B2 CLI not found! Install: pip install b2${NC}"
    exit 1
fi

# Check authentication
if ! b2 get-account-info &> /dev/null; then
    echo -e "${RED}❌ Not logged into B2!${NC}"
    echo "Run: b2 authorize-account <keyID> <applicationKey>"
    exit 1
fi

echo -e "${GREEN}✅ Authenticated with Backblaze B2${NC}"

# Create models directory
mkdir -p "$MODELS_DIR"/{wake,asr,nlu,tts,nlp}

# SHA256 function
sha256_file() {
    if command -v sha256sum &> /dev/null; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

# Upload function
upload_file() {
    local file="$1"
    local b2_path="$2"
    local name="$3"
    
    if [ ! -f "$file" ]; then
        echo -e "${YELLOW}⚠️  Missing: $file${NC}"
        return 1
    fi
    
    echo -e "${BLUE}📦 Uploading: $name${NC}"
    local sha=$(sha256_file "$file")
    echo "   SHA256: $sha"
    
    b2 upload-file "$BUCKET_NAME" "$file" "$b2_path"
    echo -e "${GREEN}✅ Uploaded: $b2_path${NC}"
    
    echo "$name|$b2_path|$sha" >> "$MODELS_DIR/manifest.txt"
}

# Initialize manifest
echo "name|path|sha256" > "$MODELS_DIR/manifest.txt"

echo ""
echo -e "${YELLOW}Uploading models to bucket: $BUCKET_NAME${NC}"
echo ""

# Upload models
upload_file "$MODELS_DIR/ya_binti_detector.tflite" "wake/ya_binti_detector.tflite" "Wake Word" || true
upload_file "$MODELS_DIR/vosk-model-ar-mgb2.zip" "asr/vosk-model-ar-mgb2.zip" "Arabic ASR" || true
upload_file "$MODELS_DIR/egybert_tiny.onnx" "nlu/egybert_tiny.onnx" "NLU Classifier" || true
upload_file "$MODELS_DIR/ar-eg-female.zip" "tts/ar-eg-female.zip" "TTS Voice" || true
upload_file "app/src/main/assets/commands/dilink_intent_map.json" "nlp/dilink_intent_map.json" "Intent Map" || true

# Create and upload manifest
cat > "$MODELS_DIR/manifest.json" << EOF
{
  "version": "1.0.0",
  "updated": "$(date -I)",
  "bucket": "$BUCKET_NAME",
  "base_url": "https://f001.backblazeb2.com/file/$BUCKET_NAME",
  "models": {
    "wake_word": {
      "file": "wake/ya_binti_detector.tflite",
      "url": "https://f001.backblazeb2.com/file/$BUCKET_NAME/wake/ya_binti_detector.tflite"
    },
    "asr": {
      "file": "asr/vosk-model-ar-mgb2.zip",
      "url": "https://f001.backblazeb2.com/file/$BUCKET_NAME/asr/vosk-model-ar-mgb2.zip"
    },
    "nlu": {
      "file": "nlu/egybert_tiny.onnx",
      "url": "https://f001.backblazeb2.com/file/$BUCKET_NAME/nlu/egybert_tiny.onnx"
    },
    "tts": {
      "file": "tts/ar-eg-female.zip",
      "url": "https://f001.backblazeb2.com/file/$BUCKET_NAME/tts/ar-eg-female.zip"
    }
  }
}
EOF

b2 upload-file "$BUCKET_NAME" "$MODELS_DIR/manifest.json" "manifest.json"

echo ""
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ Upload Complete!${NC}"
echo ""
echo "Base URL: https://f001.backblazeb2.com/file/$BUCKET_NAME"
echo ""
echo "Update ModelManager.kt:"
echo "  private const val B2_BASE_URL = \"https://f001.backblazeb2.com/file/$BUCKET_NAME\""
echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
