#!/bin/bash
#
# Binti2 B2 Cloud Storage Setup Script
# 
# This script sets up Backblaze B2 for hosting AI model files.
# Run once to configure your B2 bucket and upload models.
#
# Usage:
#   ./scripts/setup_b2.sh
#
# Required environment variables:
#   B2_KEY_ID      - Your B2 application key ID
#   B2_APP_KEY     - Your B2 application key
#
# Or pass as arguments:
#   ./scripts/setup_b2.sh <keyID> <applicationKey>
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
BUCKET_NAME="${B2_BUCKET:-binti2-models}"
MODELS_DIR="$(dirname "$0")/../models_to_upload"

echo -e "${CYAN}"
echo "╔════════════════════════════════════════════════════════════╗"
echo "║       Binti2 - Backblaze B2 Model Storage Setup            ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check for B2 CLI
check_b2_cli() {
    if command -v b2 &> /dev/null; then
        echo -e "${GREEN}✅ B2 CLI found: $(command -v b2)${NC}"
        return 0
    fi
    
    # Check venv installation
    if [ -f "/tmp/b2venv/bin/b2" ]; then
        echo -e "${GREEN}✅ B2 CLI found in venv${NC}"
        alias b2='/tmp/b2venv/bin/b2'
        return 0
    fi
    
    echo -e "${YELLOW}⚠️  B2 CLI not found. Installing...${NC}"
    python3 -m venv /tmp/b2venv
    /tmp/b2venv/bin/pip install --quiet b2
    alias b2='/tmp/b2venv/bin/b2'
    echo -e "${GREEN}✅ B2 CLI installed${NC}"
}

# Get credentials
get_credentials() {
    # Check command line args
    if [ -n "$1" ] && [ -n "$2" ]; then
        B2_KEY_ID="$1"
        B2_APP_KEY="$2"
        return 0
    fi
    
    # Check environment variables
    if [ -n "$B2_KEY_ID" ] && [ -n "$B2_APP_KEY" ]; then
        return 0
    fi
    
    # Interactive prompt
    echo -e "${YELLOW}Please enter your Backblaze B2 credentials:${NC}"
    echo -e "${BLUE}You can find these at: https://secure.backblaze.com/appkeys.htm${NC}"
    echo ""
    
    read -p "Enter Key ID: " B2_KEY_ID
    read -s -p "Enter Application Key: " B2_APP_KEY
    echo ""
    
    if [ -z "$B2_KEY_ID" ] || [ -z "$B2_APP_KEY" ]; then
        echo -e "${RED}❌ Error: Both Key ID and Application Key are required${NC}"
        exit 1
    fi
}

# Authorize with B2
authorize_b2() {
    echo -e "${BLUE}🔐 Authorizing with Backblaze B2...${NC}"
    
    if b2 authorize-account "$B2_KEY_ID" "$B2_APP_KEY" 2>&1; then
        echo -e "${GREEN}✅ Successfully authorized${NC}"
    else
        echo -e "${RED}❌ Authorization failed. Check your credentials.${NC}"
        exit 1
    fi
}

# Create bucket if not exists
create_bucket() {
    echo -e "${BLUE}📦 Checking bucket: $BUCKET_NAME${NC}"
    
    if b2 bucket list | grep -q "$BUCKET_NAME"; then
        echo -e "${GREEN}✅ Bucket '$BUCKET_NAME' already exists${NC}"
    else
        echo -e "${YELLOW}Creating bucket '$BUCKET_NAME'...${NC}"
        b2 create-bucket "$BUCKET_NAME" public
        echo -e "${GREEN}✅ Bucket created${NC}"
    fi
}

# Calculate SHA256
sha256_file() {
    if command -v sha256sum &> /dev/null; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

# Upload a file to B2
upload_file() {
    local file="$1"
    local b2_path="$2"
    local name="$3"
    
    if [ ! -f "$file" ]; then
        echo -e "${YELLOW}⚠️  File not found: $file${NC}"
        echo -e "${YELLOW}   Skipping $name${NC}"
        return 1
    fi
    
    local size=$(du -h "$file" | cut -f1)
    echo -e "${BLUE}📤 Uploading: $name ($size)${NC}"
    
    local sha=$(sha256_file "$file")
    echo "   SHA256: $sha"
    
    b2 upload-file "$BUCKET_NAME" "$file" "$b2_path"
    
    echo -e "${GREEN}✅ Uploaded: $b2_path${NC}"
    echo ""
    
    # Update manifest with SHA
    sed -i "s|\"sha256\": \"\"|\"sha256\": \"$sha\"|" "$MODELS_DIR/manifest.json" 2>/dev/null || true
}

# Upload all models
upload_models() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}Uploading Model Files${NC}"
    echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
    echo ""
    
    # Upload intent map first (always available)
    cp app/src/main/assets/commands/dilink_intent_map.json "$MODELS_DIR/nlp/"
    upload_file "$MODELS_DIR/nlp/dilink_intent_map.json" "nlp/dilink_intent_map.json" "Intent Map"
    
    # Upload model files if they exist
    upload_file "$MODELS_DIR/ya_binti_detector.tflite" "wake/ya_binti_detector.tflite" "Wake Word Detector" || true
    upload_file "$MODELS_DIR/vosk-model-ar-mgb2.zip" "asr/vosk-model-ar-mgb2.zip" "Arabic ASR Model" || true
    upload_file "$MODELS_DIR/egybert_tiny_int8.onnx" "nlu/egybert_tiny_int8.onnx" "Intent Classifier" || true
    upload_file "$MODELS_DIR/ar-eg-female.zip" "tts/ar-eg-female.zip" "TTS Voice" || true
    
    # Upload manifest
    upload_file "$MODELS_DIR/manifest.json" "manifest.json" "Model Manifest"
}

# Show completion message
show_completion() {
    echo ""
    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✅ B2 Setup Complete!${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${CYAN}Model URLs:${NC}"
    echo "  Base URL: https://f001.backblazeb2.com/file/$BUCKET_NAME"
    echo "  Manifest: https://f001.backblazeb2.com/file/$BUCKET_NAME/manifest.json"
    echo ""
    echo -e "${CYAN}Test your setup:${NC}"
    echo "  curl -I https://f001.backblazeb2.com/file/$BUCKET_NAME/manifest.json"
    echo ""
    echo -e "${YELLOW}Note: If you uploaded placeholder files, replace them with real models:${NC}"
    echo "  - Wake Word: Train with TensorFlow (see docs/B2_MODEL_SETUP.md)"
    echo "  - ASR: Download from https://alphacephei.com/vosk/models"
    echo "  - NLU: Fine-tune BERT for Egyptian Arabic"
    echo "  - TTS: Train with Coqui TTS"
    echo ""
}

# Main execution
main() {
    check_b2_cli
    get_credentials "$@"
    authorize_b2
    create_bucket
    upload_models
    show_completion
}

# Run
main "$@"
